'use client';

import React, { useEffect, useRef, useState } from 'react';
import { Memory } from '../../types/memory';
import { TranscribeResponse, VoiceRecordingState } from '../../types/voice';
import { createMemoryApi } from '../../lib/memory';
import { uploadMemoryMediaApi } from '../../lib/media';
import {
  formatDuration,
  getPreferredAudioMimeType,
  isMediaRecorderSupported,
  MAX_AUDIO_DURATION_SECONDS,
  MAX_AUDIO_SIZE_BYTES,
  transcribeAudioApi,
  validateAudioBlob,
} from '../../lib/voice';

export interface VoiceRecorderModalProps {
  isOpen: boolean;
  onClose: () => void;
  onMemoryCreated?: (memory: Memory) => void;
  onTranscriptReady?: (transcript: string) => void;
  initialCategory?: string;
  initialTags?: string;
}

export default function VoiceRecorderModal({
  isOpen,
  onClose,
  onMemoryCreated,
  onTranscriptReady,
  initialCategory = 'Reflections',
  initialTags = 'voice, reflection',
}: VoiceRecorderModalProps) {
  const [recordingState, setRecordingState] = useState<VoiceRecordingState>('idle');
  const [elapsedSeconds, setElapsedSeconds] = useState<number>(0);
  const [audioBlob, setAudioBlob] = useState<Blob | null>(null);
  const [audioUrl, setAudioUrl] = useState<string | null>(null);
  const [transcriptResponse, setTranscriptResponse] = useState<TranscribeResponse | null>(null);
  const [isBrowserSupported, setIsBrowserSupported] = useState<boolean>(true);

  // Form Fields for Memory Creation
  const [title, setTitle] = useState<string>('');
  const [content, setContent] = useState<string>('');
  const [category, setCategory] = useState<string>(initialCategory);
  const [tags, setTags] = useState<string>(initialTags);

  // Status & Error
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [pendingSavedMemory, setPendingSavedMemory] = useState<Memory | null>(null);
  const [audioUploadError, setAudioUploadError] = useState<string | null>(null);

  // Refs for audio handling and cleanup
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const mediaStreamRef = useRef<MediaStream | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const timerIntervalRef = useRef<NodeJS.Timeout | null>(null);
  const audioUrlRef = useRef<string | null>(null);
  audioUrlRef.current = audioUrl;
  const recordingStartTimeRef = useRef<number>(0);
  const prevIsOpenRef = useRef<boolean>(false);
  const isOpenRef = useRef<boolean>(isOpen);
  isOpenRef.current = isOpen;

  // Verify browser capability on mount
  useEffect(() => {
    setIsBrowserSupported(isMediaRecorderSupported());
  }, []);

  // Reset when modal opens or closes, preserving active recording on prop changes
  useEffect(() => {
    if (isOpen && !prevIsOpenRef.current) {
      setRecordingState('idle');
      setElapsedSeconds(0);
      setAudioBlob(null);
      if (audioUrlRef.current) {
        URL.revokeObjectURL(audioUrlRef.current);
        audioUrlRef.current = null;
      }
      setAudioUrl(null);
      setTranscriptResponse(null);
      setErrorMessage(null);
      setSuccessMessage(null);
      setIsSubmitting(false);
      setPendingSavedMemory(null);
      setAudioUploadError(null);

      const now = new Date();
      const dateStr = now.toLocaleDateString(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      });
      const timeStr = now.toLocaleTimeString(undefined, {
        hour: '2-digit',
        minute: '2-digit',
      });
      setTitle(`Voice Reflection - ${dateStr} at ${timeStr}`);
      setContent('');
      setCategory(initialCategory);
      setTags(initialTags);
    } else if (!isOpen && prevIsOpenRef.current) {
      cleanupAudioSession();
      if (audioUrlRef.current) {
        URL.revokeObjectURL(audioUrlRef.current);
        audioUrlRef.current = null;
      }
      setAudioUrl(null);
      setAudioBlob(null);
      setPendingSavedMemory(null);
      setAudioUploadError(null);
      setRecordingState('idle');
    }
    prevIsOpenRef.current = isOpen;
  }, [isOpen]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      cleanupAudioSession();
      if (audioUrlRef.current) {
        URL.revokeObjectURL(audioUrlRef.current);
        audioUrlRef.current = null;
      }
    };
  }, []);

  const cleanupAudioSession = () => {
    if (timerIntervalRef.current) {
      clearInterval(timerIntervalRef.current);
      timerIntervalRef.current = null;
    }
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      try {
        mediaRecorderRef.current.stop();
      } catch {
        // ignore on cleanup
      }
    }
    if (mediaStreamRef.current) {
      mediaStreamRef.current.getTracks().forEach((track) => track.stop());
      mediaStreamRef.current = null;
    }
  };

  const handleStartRecording = async () => {
    if (!isBrowserSupported) {
      setErrorMessage(
        'Audio recording is not supported in this browser. Please use Chrome, Firefox, Safari, or Edge.'
      );
      return;
    }

    setErrorMessage(null);
    setAudioBlob(null);
    if (audioUrlRef.current) {
      URL.revokeObjectURL(audioUrlRef.current);
      audioUrlRef.current = null;
    }
    setAudioUrl(null);
    setTranscriptResponse(null);
    audioChunksRef.current = [];

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      mediaStreamRef.current = stream;

      const mimeType = getPreferredAudioMimeType();
      const recorderOptions: MediaRecorderOptions = {};
      if (mimeType) {
        recorderOptions.mimeType = mimeType;
      }

      const recorder = new MediaRecorder(stream, recorderOptions);
      mediaRecorderRef.current = recorder;

      recorder.ondataavailable = (event: BlobEvent) => {
        if (event.data && event.data.size > 0) {
          audioChunksRef.current.push(event.data);
        }
      };

      recorder.onstop = () => {
        // 1. Clear elapsed timer interval and reset ref
        if (timerIntervalRef.current) {
          clearInterval(timerIntervalRef.current);
          timerIntervalRef.current = null;
        }

        // 2. Release microphone hardware
        if (mediaStreamRef.current) {
          mediaStreamRef.current.getTracks().forEach((track) => track.stop());
          mediaStreamRef.current = null;
        }

        // If the modal was closed while recording was in flight, abort preview creation
        if (!isOpenRef.current) {
          return;
        }

        // 3. Preserve the final elapsed recording duration
        if (recordingStartTimeRef.current > 0) {
          const finalDuration = Math.floor((Date.now() - recordingStartTimeRef.current) / 1000);
          setElapsedSeconds(finalDuration);
        }

        const recordedType = recorder.mimeType || mimeType || 'audio/webm';
        const blob = new Blob(audioChunksRef.current, { type: recordedType });

        const validation = validateAudioBlob(blob);
        if (!validation.valid) {
          setErrorMessage(validation.error || 'Invalid audio recording.');
          setRecordingState('error');
          return;
        }

        setAudioBlob(blob);
        const url = URL.createObjectURL(blob);
        audioUrlRef.current = url;
        setAudioUrl(url);
        setRecordingState('recorded');
      };

      recorder.start(250); // collect chunks every 250ms
      setRecordingState('recording');
      setElapsedSeconds(0);

      // Start elapsed timer
      recordingStartTimeRef.current = Date.now();
      timerIntervalRef.current = setInterval(() => {
        const seconds = Math.floor((Date.now() - recordingStartTimeRef.current) / 1000);
        setElapsedSeconds(seconds);

        // Auto-stop if reaching maximum allowed duration
        if (seconds >= MAX_AUDIO_DURATION_SECONDS) {
          handleStopRecording();
        }
      }, 500);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Microphone error';
      if (msg.includes('Permission') || msg.includes('denied') || msg.includes('NotAllowedError')) {
        setErrorMessage(
          'Microphone permission was denied. Please allow microphone access in your browser to record audio.'
        );
      } else if (msg.includes('NotFound') || msg.includes('DevicesNotFoundError')) {
        setErrorMessage('No microphone device was detected on your system.');
      } else {
        setErrorMessage(`Could not start audio recording: ${msg}`);
      }
      setRecordingState('error');
      cleanupAudioSession();
    }
  };

  const handleStopRecording = () => {
    if (timerIntervalRef.current) {
      clearInterval(timerIntervalRef.current);
      timerIntervalRef.current = null;
    }
    if (recordingStartTimeRef.current > 0) {
      const finalDuration = Math.floor((Date.now() - recordingStartTimeRef.current) / 1000);
      setElapsedSeconds(finalDuration);
    }
    if (mediaRecorderRef.current && mediaRecorderRef.current.state !== 'inactive') {
      mediaRecorderRef.current.stop();
    }
  };

  const handleTranscribe = async () => {
    if (!audioBlob) {
      setErrorMessage('No recorded audio available to transcribe.');
      return;
    }
    if (isSubmitting) return;

    setIsSubmitting(true);
    setRecordingState('transcribing');
    setErrorMessage(null);

    try {
      const ext = audioBlob.type?.includes('mp4') ? 'm4a' : 'webm';
      const result = await transcribeAudioApi(audioBlob, `reflection_${Date.now()}.${ext}`);
      setTranscriptResponse(result);
      setContent(result.transcript);
      setRecordingState('transcribed');
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Transcription failed.';
      setErrorMessage(msg);
      setRecordingState('recorded'); // Allow user to retry transcribing or re-record
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleSaveMemory = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (isSubmitting) return;

    if (!title.trim() || !content.trim()) {
      setErrorMessage('Both Title and Content (transcript) are required to save a memory.');
      return;
    }

    setIsSubmitting(true);
    setRecordingState('saving');
    setErrorMessage(null);
    setAudioUploadError(null);

    let memoryToUse = pendingSavedMemory;

    try {
      // Step 1: Create Memory record if not already created in a previous partial attempt
      if (!memoryToUse) {
        memoryToUse = await createMemoryApi({
          title: title.trim(),
          content: content.trim(),
          category: category.trim() || undefined,
          tags: tags.trim() || undefined,
        });
        setPendingSavedMemory(memoryToUse);
        if (onMemoryCreated) {
          onMemoryCreated(memoryToUse);
        }
      }

      // Step 2: Upload the recorded audio Blob if present
      if (audioBlob) {
        const baseType = audioBlob.type?.split(';')[0]?.toLowerCase() || 'audio/webm';
        let ext = 'webm';
        if (baseType.includes('mp4') || baseType.includes('m4a')) ext = 'm4a';
        else if (baseType.includes('wav')) ext = 'wav';
        else if (baseType.includes('ogg')) ext = 'ogg';
        else if (baseType.includes('mpeg') || baseType.includes('mp3')) ext = 'mp3';

        const audioFile = new File([audioBlob], `voice_recording_${Date.now()}.${ext}`, {
          type: baseType,
        });

        await uploadMemoryMediaApi(memoryToUse.id, [audioFile]);
      }

      // Step 3: Success!
      setRecordingState('saved');
      setPendingSavedMemory(null);
      setAudioUploadError(null);
      setSuccessMessage('Voice memory and audio recording saved to your vault!');

      if (onTranscriptReady) {
        onTranscriptReady(content.trim());
      }

      // Close modal after brief success presentation
      setTimeout(() => {
        onClose();
      }, 1500);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Operation failed.';
      if (memoryToUse) {
        // Memory exists, but audio upload failed: preserve created memory for retry
        setAudioUploadError(msg);
        setErrorMessage(
          `Memory "${memoryToUse.title}" was saved, but attaching the voice recording failed: ${msg}. You can retry attaching the audio below, or click Done to finish with text only.`
        );
      } else {
        setErrorMessage(`Failed to save memory: ${msg}`);
      }
      setRecordingState('transcribed');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleUseTranscriptInForm = () => {
    if (onTranscriptReady && content.trim()) {
      onTranscriptReady(content.trim());
      onClose();
    }
  };

  if (!isOpen) return null;

  return (
    <div
      onClick={(e) => {
        if (e.target === e.currentTarget && !isSubmitting && recordingState !== 'recording') {
          onClose();
        }
      }}
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(62, 39, 35, 0.48)',
        backdropFilter: 'blur(8px)',
        WebkitBackdropFilter: 'blur(8px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '1rem',
        zIndex: 110,
      }}
    >
      <div
        style={{
          backgroundColor: 'var(--surface)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-lg)',
          width: '100%',
          maxWidth: '580px',
          maxHeight: '90vh',
          overflowY: 'auto',
          padding: '2rem',
          boxShadow: 'var(--shadow-soft)',
          display: 'flex',
          flexDirection: 'column',
          gap: '1.25rem',
        }}
      >
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <div
              style={{
                width: '42px',
                height: '42px',
                borderRadius: '12px',
                background: 'linear-gradient(135deg, rgba(182, 90, 70, 0.15), rgba(74, 142, 139, 0.15))',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '1.35rem',
              }}
            >
              🎙️
            </div>
            <div>
              <span className="eyebrow" style={{ fontSize: '0.68rem', padding: '0.2rem 0.6rem' }}>Voice Reflection</span>
              <h2 style={{ fontSize: '1.3rem', fontWeight: 700, color: 'var(--text)', margin: '0.15rem 0 0 0', letterSpacing: '-0.02em' }}>
                Record Voice Memory
              </h2>
            </div>
          </div>
          <button
            type="button"
            disabled={isSubmitting || recordingState === 'recording'}
            onClick={onClose}
            aria-label="Close voice recorder"
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--text-muted)',
              fontSize: '1.25rem',
              cursor: isSubmitting || recordingState === 'recording' ? 'not-allowed' : 'pointer',
              padding: '0.35rem',
              borderRadius: '50%',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              lineHeight: 1,
            }}
          >
            ✕
          </button>
        </div>

        {/* Browser Support Warning */}
        {!isBrowserSupported && (
          <div
            style={{
              backgroundColor: 'var(--danger-background)',
              border: '1px solid var(--danger-border)',
              borderRadius: 'var(--radius-sm)',
              padding: '0.85rem 1rem',
              color: 'var(--danger)',
              fontSize: '0.85rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.6rem',
            }}
          >
            <span style={{ fontSize: '1.2rem' }}>⚠️</span>
            <div>
              <strong>Browser audio recording unsupported:</strong> Your browser does not support the
              MediaRecorder audio API. Please use Google Chrome, Mozilla Firefox, Safari, or Microsoft
              Edge.
            </div>
          </div>
        )}

        {/* Error Alert */}
        {errorMessage && (
          <div
            style={{
              backgroundColor: 'var(--danger-background)',
              border: '1px solid var(--danger-border)',
              borderRadius: 'var(--radius-sm)',
              padding: '0.75rem 1rem',
              color: 'var(--danger)',
              fontSize: '0.85rem',
              display: 'flex',
              alignItems: 'flex-start',
              gap: '0.6rem',
            }}
          >
            <span style={{ fontSize: '1.1rem' }}>⚠️</span>
            <div style={{ flex: 1, fontWeight: 500 }}>{errorMessage}</div>
            <button
              type="button"
              onClick={() => setErrorMessage(null)}
              style={{
                background: 'none',
                border: 'none',
                color: 'var(--danger)',
                cursor: 'pointer',
                fontWeight: 700,
              }}
            >
              ✕
            </button>
          </div>
        )}

        {/* Success Alert */}
        {successMessage && (
          <div
            style={{
              backgroundColor: 'var(--success-background)',
              border: '1px solid var(--success-border)',
              borderRadius: 'var(--radius-sm)',
              padding: '0.75rem 1rem',
              color: 'var(--accent-strong)',
              fontSize: '0.85rem',
              display: 'flex',
              alignItems: 'center',
              gap: '0.6rem',
            }}
          >
            <span style={{ fontSize: '1.2rem' }}>✓</span>
            <div style={{ fontWeight: 600 }}>{successMessage}</div>
          </div>
        )}

        {/* RECORDING / CONTROLS PANEL */}
        {recordingState !== 'transcribed' && recordingState !== 'saving' && recordingState !== 'saved' && (
          <div
            style={{
              backgroundColor: 'var(--surface-muted)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius-md)',
              padding: '1.75rem 1.5rem',
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '1.2rem',
              textAlign: 'center',
            }}
          >
            {/* RECORDING STATE */}
            {recordingState === 'recording' && (
              <>
                <div style={{ position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                  <div
                    style={{
                      width: '76px',
                      height: '76px',
                      borderRadius: '50%',
                      background: 'linear-gradient(135deg, var(--primary), #c56c58)',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      color: '#ffffff',
                      fontSize: '2rem',
                      boxShadow: '0 0 0 10px rgba(182, 90, 70, 0.22)',
                    }}
                  >
                    🎙️
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: '1.5rem', fontWeight: 700, color: 'var(--primary)', letterSpacing: '0.05em' }}>
                    {formatDuration(elapsedSeconds)} / {formatDuration(MAX_AUDIO_DURATION_SECONDS)}
                  </div>
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>
                    Recording audio... (max {MAX_AUDIO_DURATION_SECONDS / 60} minutes)
                  </div>
                </div>

                <div style={{ width: '100%', maxWidth: '280px', backgroundColor: 'var(--border)', height: '6px', borderRadius: '3px', overflow: 'hidden' }}>
                  <div
                    style={{
                      height: '100%',
                      background: 'linear-gradient(90deg, var(--primary), var(--accent))',
                      width: `${Math.min(100, (elapsedSeconds / MAX_AUDIO_DURATION_SECONDS) * 100)}%`,
                      transition: 'width 0.5s ease',
                    }}
                  />
                </div>

                <button
                  type="button"
                  className="primary-button"
                  onClick={handleStopRecording}
                  style={{
                    background: 'linear-gradient(135deg, var(--danger), #b85447)',
                    boxShadow: '0 8px 18px rgba(165, 72, 59, 0.25)',
                    border: 'none',
                    padding: '0.7rem 1.6rem',
                  }}
                >
                  <span>■</span>
                  <span>Stop Recording</span>
                </button>
              </>
            )}

            {/* TRANSCRIBING STATE */}
            {recordingState === 'transcribing' && (
              <div style={{ padding: '1rem 0', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.8rem' }}>
                <div
                  style={{
                    width: '44px',
                    height: '44px',
                    border: '4px solid rgba(182, 90, 70, 0.15)',
                    borderTop: '4px solid var(--primary)',
                    borderRadius: '50%',
                    animation: 'spin 1s linear infinite',
                  }}
                />
                <style>{`@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }`}</style>
                <div style={{ fontWeight: 700, color: 'var(--text)', fontSize: '0.98rem' }}>
                  Transcribing speech with MemoraAI AI...
                </div>
                <div style={{ fontSize: '0.82rem', color: 'var(--text-muted)' }}>
                  Processing audio stream through Whisper speech recognition. Please wait.
                </div>
              </div>
            )}

            {/* RECORDED PREVIEW STATE */}
            {recordingState === 'recorded' && audioUrl && (
              <>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: 'var(--accent-strong)', fontWeight: 700, fontSize: '0.95rem' }}>
                  <span>✓</span>
                  <span>Recording ready ({formatDuration(elapsedSeconds)})</span>
                </div>

                <audio
                  controls
                  src={audioUrl}
                  style={{ width: '100%', maxWidth: '420px', borderRadius: 'var(--radius-sm)' }}
                />

                <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', justifyContent: 'center' }}>
                  <button
                    type="button"
                    className="secondary-button"
                    disabled={isSubmitting}
                    onClick={handleStartRecording}
                    style={{ padding: '0.65rem 1.25rem' }}
                  >
                    🔄 Re-record
                  </button>

                  <button
                    type="button"
                    className="primary-button"
                    disabled={isSubmitting}
                    onClick={handleTranscribe}
                    style={{ padding: '0.65rem 1.5rem' }}
                  >
                    <span>✨</span>
                    <span>Transcribe Audio</span>
                  </button>
                </div>
              </>
            )}

            {/* IDLE / READY TO RECORD STATE */}
            {(recordingState === 'idle' || recordingState === 'error') && (
              <>
                <div
                  onClick={isBrowserSupported ? handleStartRecording : undefined}
                  style={{
                    width: '84px',
                    height: '84px',
                    borderRadius: '50%',
                    background: isBrowserSupported
                      ? 'linear-gradient(135deg, rgba(182, 90, 70, 0.14), rgba(74, 142, 139, 0.14))'
                      : 'var(--border)',
                    color: isBrowserSupported ? 'var(--primary)' : 'var(--disabled)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '2.4rem',
                    cursor: isBrowserSupported ? 'pointer' : 'not-allowed',
                    transition: 'all 0.2s ease',
                    boxShadow: isBrowserSupported ? '0 8px 20px rgba(182, 90, 70, 0.18)' : 'none',
                    border: '1px solid var(--border)',
                  }}
                  title={isBrowserSupported ? 'Click to start recording' : 'Recording unsupported'}
                >
                  🎙️
                </div>

                <div>
                  <div style={{ fontWeight: 700, color: 'var(--text)', fontSize: '1.05rem', letterSpacing: '-0.02em' }}>
                    {isBrowserSupported ? 'Click to Start Voice Recording' : 'Recording Unavailable'}
                  </div>
                  <div style={{ fontSize: '0.82rem', color: 'var(--text-muted)', maxWidth: '380px', marginTop: '0.35rem' }}>
                    Speak into your microphone. When finished, MemoraAI will transcribe your reflection into text.
                    Limit: 5 minutes ({MAX_AUDIO_SIZE_BYTES / (1024 * 1024)} MB).
                  </div>
                </div>

                {isBrowserSupported && (
                  <button
                    type="button"
                    className="primary-button"
                    onClick={handleStartRecording}
                    style={{ padding: '0.75rem 1.6rem' }}
                  >
                    <span>🎙️</span>
                    <span>Start Recording</span>
                  </button>
                )}
              </>
            )}
          </div>
        )}

        {/* TRANSCRIBED / EDIT & SAVE FORM */}
        {(recordingState === 'transcribed' || recordingState === 'saving' || recordingState === 'saved') && (
          <form onSubmit={handleSaveMemory} style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {/* Audio Playback reference */}
            {audioUrl && (
              <div
                style={{
                  backgroundColor: 'var(--surface-muted)',
                  border: '1px solid var(--border)',
                  borderRadius: 'var(--radius-sm)',
                  padding: '0.75rem 1rem',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: '0.75rem',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.45rem', fontSize: '0.82rem', color: 'var(--text-muted)', fontWeight: 600 }}>
                  <span>🎧</span>
                  <span>Audio Reference:</span>
                </div>
                <audio controls src={audioUrl} style={{ height: '34px', flex: 1, maxWidth: '340px' }} />
              </div>
            )}

            {/* Provider Disclaimer / Genuine Speech vs Simulation Banner (Requirement 9) */}
            {transcriptResponse && (
              transcriptResponse.isSimulated ? (
                <div
                  style={{
                    backgroundColor: 'var(--warning-background)',
                    border: '1px solid var(--warning-border)',
                    borderRadius: 'var(--radius-sm)',
                    padding: '0.75rem 1rem',
                    color: 'var(--warning)',
                    fontSize: '0.825rem',
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '0.65rem',
                  }}
                >
                  <span style={{ fontSize: '1.15rem' }}>⚠️</span>
                  <div>
                    <div style={{ fontWeight: 700 }}>Simulated Speech Transcription (Mock Provider)</div>
                    <div style={{ marginTop: '0.2rem', color: 'var(--text)' }}>
                      This transcript was generated by the development mock provider, not genuine speech
                      recognition. To enable genuine speech transcription, set{' '}
                      <code style={{ background: 'rgba(255, 255, 255, 0.6)', padding: '0.1rem 0.3rem', borderRadius: '4px' }}>STT_PROVIDER=whisper-local</code> in your backend environment.
                    </div>
                  </div>
                </div>
              ) : (
                <div
                  style={{
                    backgroundColor: 'var(--success-background)',
                    border: '1px solid var(--success-border)',
                    borderRadius: 'var(--radius-sm)',
                    padding: '0.75rem 1rem',
                    color: 'var(--accent-strong)',
                    fontSize: '0.825rem',
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '0.65rem',
                  }}
                >
                  <span style={{ fontSize: '1.15rem' }}>✓</span>
                  <div>
                    <div style={{ fontWeight: 700 }}>Genuine Speech Recognition</div>
                    <div style={{ marginTop: '0.2rem', color: 'var(--text)' }}>
                      Transcribed with <strong>{transcriptResponse.provider}</strong> (Language:{' '}
                      {transcriptResponse.detectedLanguage.toUpperCase()}, Duration:{' '}
                      {transcriptResponse.durationSeconds.toFixed(1)}s).
                    </div>
                  </div>
                </div>
              )
            )}

            {/* Notice about storage limitations */}
            <div
              style={{
                backgroundColor: 'var(--surface-muted)',
                border: '1px solid var(--border)',
                borderRadius: 'var(--radius-sm)',
                padding: '0.6rem 0.9rem',
                fontSize: '0.78rem',
                color: 'var(--text-muted)',
              }}
            >
              ℹ️ <strong>Storage Note:</strong> Audio is converted to text memory content and attached to your memory.
            </div>

            {/* Editable Title */}
            <div>
              <label
                style={{
                  display: 'block',
                  fontSize: '0.8rem',
                  fontWeight: 700,
                  letterSpacing: '0.04em',
                  textTransform: 'uppercase',
                  color: 'var(--text-muted)',
                  marginBottom: '0.4rem',
                }}
              >
                Memory Title *
              </label>
              <input
                type="text"
                required
                disabled={isSubmitting || recordingState === 'saved'}
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Give your voice memory a title..."
                style={{
                  width: '100%',
                  boxSizing: 'border-box',
                  padding: '0.75rem 0.95rem',
                  border: '1px solid var(--control-border)',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: '0.92rem',
                  outline: 'none',
                  backgroundColor: 'var(--surface)',
                  color: 'var(--text)',
                }}
              />
            </div>

            {/* Editable Content (Pre-filled with transcript) */}
            <div>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  marginBottom: '0.4rem',
                }}
              >
                <label
                  style={{
                    fontSize: '0.8rem',
                    fontWeight: 700,
                    letterSpacing: '0.04em',
                    textTransform: 'uppercase',
                    color: 'var(--text-muted)',
                  }}
                >
                  Transcript Content * (Edit before saving)
                </label>
                <span
                  className="chip"
                  style={{
                    fontSize: '0.72rem',
                    padding: '0.2rem 0.55rem',
                    border: '1px solid var(--border)',
                  }}
                >
                  ✏️ Freely editable
                </span>
              </div>
              <textarea
                required
                rows={5}
                disabled={isSubmitting || recordingState === 'saved'}
                value={content}
                onChange={(e) => setContent(e.target.value)}
                placeholder="Review and edit the speech transcript here..."
                style={{
                  width: '100%',
                  boxSizing: 'border-box',
                  padding: '0.75rem 0.95rem',
                  border: '1px solid var(--control-border)',
                  borderRadius: 'var(--radius-sm)',
                  fontSize: '0.92rem',
                  outline: 'none',
                  resize: 'vertical',
                  fontFamily: 'inherit',
                  lineHeight: '1.5',
                  backgroundColor: 'var(--surface)',
                  color: 'var(--text)',
                }}
              />
            </div>

            {/* Optional Category & Tags */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
              <div>
                <label
                  style={{
                    display: 'block',
                    fontSize: '0.8rem',
                    fontWeight: 700,
                    letterSpacing: '0.04em',
                    textTransform: 'uppercase',
                    color: 'var(--text-muted)',
                    marginBottom: '0.35rem',
                  }}
                >
                  Category (optional)
                </label>
                <input
                  type="text"
                  disabled={isSubmitting || recordingState === 'saved'}
                  value={category}
                  onChange={(e) => setCategory(e.target.value)}
                  placeholder="e.g., Reflections, Voice Note"
                  style={{
                    width: '100%',
                    boxSizing: 'border-box',
                    padding: '0.65rem 0.85rem',
                    border: '1px solid var(--control-border)',
                    borderRadius: 'var(--radius-sm)',
                    fontSize: '0.88rem',
                    outline: 'none',
                    backgroundColor: 'var(--surface)',
                    color: 'var(--text)',
                  }}
                />
              </div>

              <div>
                <label
                  style={{
                    display: 'block',
                    fontSize: '0.8rem',
                    fontWeight: 700,
                    letterSpacing: '0.04em',
                    textTransform: 'uppercase',
                    color: 'var(--text-muted)',
                    marginBottom: '0.35rem',
                  }}
                >
                  Tags (optional)
                </label>
                <input
                  type="text"
                  disabled={isSubmitting || recordingState === 'saved'}
                  value={tags}
                  onChange={(e) => setTags(e.target.value)}
                  placeholder="e.g., voice, audio, 2026"
                  style={{
                    width: '100%',
                    boxSizing: 'border-box',
                    padding: '0.65rem 0.85rem',
                    border: '1px solid var(--control-border)',
                    borderRadius: 'var(--radius-sm)',
                    fontSize: '0.88rem',
                    outline: 'none',
                    backgroundColor: 'var(--surface)',
                    color: 'var(--text)',
                  }}
                />
              </div>
            </div>

            {/* Actions */}
            <div
              style={{
                display: 'flex',
                justifyContent: 'flex-end',
                gap: '0.75rem',
                marginTop: '0.75rem',
                flexWrap: 'wrap',
              }}
            >
              {onTranscriptReady && !pendingSavedMemory && (
                <button
                  type="button"
                  className="ghost-button"
                  disabled={isSubmitting || recordingState === 'saved'}
                  onClick={handleUseTranscriptInForm}
                  style={{
                    padding: '0.65rem 1.25rem',
                    fontSize: '0.875rem',
                  }}
                >
                  Insert into Form Content
                </button>
              )}

              {pendingSavedMemory ? (
                <>
                  <button
                    type="button"
                    className="secondary-button"
                    disabled={isSubmitting}
                    onClick={() => {
                      setPendingSavedMemory(null);
                      onClose();
                    }}
                    style={{
                      padding: '0.65rem 1.25rem',
                      fontSize: '0.875rem',
                    }}
                  >
                    Done (Keep Text Only)
                  </button>

                  <button
                    type="button"
                    className="primary-button"
                    disabled={isSubmitting}
                    onClick={() => handleSaveMemory()}
                    style={{
                      padding: '0.65rem 1.4rem',
                      fontSize: '0.875rem',
                    }}
                  >
                    {isSubmitting ? (
                      <span>Retrying Audio Upload...</span>
                    ) : (
                      <>
                        <span>🔄</span>
                        <span>Retry Audio Upload</span>
                      </>
                    )}
                  </button>
                </>
              ) : (
                <>
                  <button
                    type="button"
                    className="secondary-button"
                    disabled={isSubmitting || recordingState === 'saved'}
                    onClick={onClose}
                    style={{
                      padding: '0.65rem 1.25rem',
                      fontSize: '0.875rem',
                    }}
                  >
                    Cancel
                  </button>

                  <button
                    type="submit"
                    className="primary-button"
                    disabled={isSubmitting || recordingState === 'saved'}
                    style={{
                      padding: '0.65rem 1.5rem',
                      fontSize: '0.875rem',
                      opacity: isSubmitting || recordingState === 'saved' ? 0.7 : 1,
                      cursor: isSubmitting || recordingState === 'saved' ? 'not-allowed' : 'pointer',
                    }}
                  >
                    {isSubmitting ? (
                      <span>Saving to Vault...</span>
                    ) : recordingState === 'saved' ? (
                      <span>✓ Saved!</span>
                    ) : (
                      <>
                        <span>💾</span>
                        <span>Save to Vault</span>
                      </>
                    )}
                  </button>
                </>
              )}
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
