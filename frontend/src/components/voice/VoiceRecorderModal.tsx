'use client';

import React, { useEffect, useRef, useState } from 'react';
import { Memory } from '../../types/memory';
import { TranscribeResponse, VoiceRecordingState } from '../../types/voice';
import { createMemoryApi } from '../../lib/memory';
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

  const handleSaveMemory = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isSubmitting) return;

    if (!title.trim() || !content.trim()) {
      setErrorMessage('Both Title and Content (transcript) are required to save a memory.');
      return;
    }

    setIsSubmitting(true);
    setRecordingState('saving');
    setErrorMessage(null);

    try {
      const savedMemory = await createMemoryApi({
        title: title.trim(),
        content: content.trim(),
        category: category.trim() || undefined,
        tags: tags.trim() || undefined,
      });

      setRecordingState('saved');
      setSuccessMessage('Voice memory successfully saved to your vault!');

      if (onMemoryCreated) {
        onMemoryCreated(savedMemory);
      }
      if (onTranscriptReady) {
        onTranscriptReady(content.trim());
      }

      // Close modal after brief success presentation
      setTimeout(() => {
        onClose();
      }, 1500);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to save memory.';
      setErrorMessage(msg);
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
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        backgroundColor: 'rgba(15, 23, 42, 0.6)',
        backdropFilter: 'blur(2px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '1rem',
        zIndex: 110,
      }}
    >
      <div
        style={{
          backgroundColor: '#ffffff',
          borderRadius: '16px',
          width: '100%',
          maxWidth: '580px',
          maxHeight: '90vh',
          overflowY: 'auto',
          padding: '2rem',
          boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
          display: 'flex',
          flexDirection: 'column',
          gap: '1.25rem',
        }}
      >
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.6rem' }}>
            <span style={{ fontSize: '1.5rem' }}>🎙️</span>
            <div>
              <h2 style={{ fontSize: '1.25rem', fontWeight: 700, color: '#0f172a', margin: 0 }}>
                Record Voice Memory
              </h2>
              <p style={{ fontSize: '0.8rem', color: '#64748b', margin: '0.15rem 0 0 0' }}>
                Capture audio reflections and automatically transcribe into your vault
              </p>
            </div>
          </div>
          <button
            type="button"
            disabled={isSubmitting || recordingState === 'recording'}
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              color: '#94a3b8',
              fontSize: '1.25rem',
              cursor: isSubmitting || recordingState === 'recording' ? 'not-allowed' : 'pointer',
              padding: '0.25rem',
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
              backgroundColor: '#fee2e2',
              border: '1px solid #fca5a5',
              borderRadius: '10px',
              padding: '0.85rem 1rem',
              color: '#991b1b',
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
              backgroundColor: '#fef2f2',
              border: '1px solid #fecaca',
              borderRadius: '10px',
              padding: '0.75rem 1rem',
              color: '#b91c1c',
              fontSize: '0.85rem',
              display: 'flex',
              alignItems: 'flex-start',
              gap: '0.6rem',
            }}
          >
            <span style={{ fontSize: '1.1rem' }}>⚠️</span>
            <div style={{ flex: 1 }}>{errorMessage}</div>
            <button
              type="button"
              onClick={() => setErrorMessage(null)}
              style={{
                background: 'none',
                border: 'none',
                color: '#b91c1c',
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
              backgroundColor: '#f0fdf4',
              border: '1px solid #bbf7d0',
              borderRadius: '10px',
              padding: '0.75rem 1rem',
              color: '#15803d',
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
              backgroundColor: '#f8fafc',
              border: '1px solid #e2e8f0',
              borderRadius: '12px',
              padding: '1.5rem',
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
                      width: '72px',
                      height: '72px',
                      borderRadius: '50%',
                      backgroundColor: '#ef4444',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      color: '#ffffff',
                      fontSize: '2rem',
                      boxShadow: '0 0 0 10px rgba(239, 68, 68, 0.2)',
                    }}
                  >
                    🎙️
                  </div>
                </div>

                <div>
                  <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#ef4444', letterSpacing: '0.05em' }}>
                    {formatDuration(elapsedSeconds)} / {formatDuration(MAX_AUDIO_DURATION_SECONDS)}
                  </div>
                  <div style={{ fontSize: '0.8rem', color: '#64748b', marginTop: '0.2rem' }}>
                    Recording audio... (max {MAX_AUDIO_DURATION_SECONDS / 60} minutes)
                  </div>
                </div>

                <div style={{ width: '100%', maxWidth: '280px', backgroundColor: '#e2e8f0', height: '6px', borderRadius: '3px', overflow: 'hidden' }}>
                  <div
                    style={{
                      height: '100%',
                      backgroundColor: '#ef4444',
                      width: `${Math.min(100, (elapsedSeconds / MAX_AUDIO_DURATION_SECONDS) * 100)}%`,
                      transition: 'width 0.5s ease',
                    }}
                  />
                </div>

                <button
                  type="button"
                  onClick={handleStopRecording}
                  style={{
                    backgroundColor: '#ef4444',
                    color: '#ffffff',
                    border: 'none',
                    padding: '0.65rem 1.5rem',
                    borderRadius: '8px',
                    fontWeight: 600,
                    fontSize: '0.9rem',
                    cursor: 'pointer',
                    boxShadow: '0 2px 4px rgba(239, 68, 68, 0.3)',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '0.5rem',
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
                    width: '42px',
                    height: '42px',
                    border: '4px solid #e0f2fe',
                    borderTop: '4px solid #0284c7',
                    borderRadius: '50%',
                    animation: 'spin 1s linear infinite',
                  }}
                />
                <style>{`@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }`}</style>
                <div style={{ fontWeight: 600, color: '#0f172a', fontSize: '0.95rem' }}>
                  Transcribing speech with MemoraAI AI...
                </div>
                <div style={{ fontSize: '0.8rem', color: '#64748b' }}>
                  Processing audio stream through Whisper speech recognition. Please wait.
                </div>
              </div>
            )}

            {/* RECORDED PREVIEW STATE */}
            {recordingState === 'recorded' && audioUrl && (
              <>
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', color: '#059669', fontWeight: 600, fontSize: '0.95rem' }}>
                  <span>✓</span>
                  <span>Recording ready ({formatDuration(elapsedSeconds)})</span>
                </div>

                <audio
                  controls
                  src={audioUrl}
                  style={{ width: '100%', maxWidth: '420px', borderRadius: '8px' }}
                />

                <div style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', justifyContent: 'center' }}>
                  <button
                    type="button"
                    disabled={isSubmitting}
                    onClick={handleStartRecording}
                    style={{
                      backgroundColor: '#ffffff',
                      color: '#475569',
                      border: '1px solid #cbd5e1',
                      padding: '0.6rem 1.1rem',
                      borderRadius: '8px',
                      fontWeight: 600,
                      fontSize: '0.875rem',
                      cursor: isSubmitting ? 'not-allowed' : 'pointer',
                    }}
                  >
                    🔄 Re-record
                  </button>

                  <button
                    type="button"
                    disabled={isSubmitting}
                    onClick={handleTranscribe}
                    style={{
                      backgroundColor: '#0284c7',
                      color: '#ffffff',
                      border: 'none',
                      padding: '0.6rem 1.4rem',
                      borderRadius: '8px',
                      fontWeight: 600,
                      fontSize: '0.875rem',
                      cursor: isSubmitting ? 'not-allowed' : 'pointer',
                      boxShadow: '0 2px 4px rgba(2, 132, 199, 0.25)',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.4rem',
                    }}
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
                    width: '80px',
                    height: '80px',
                    borderRadius: '50%',
                    backgroundColor: isBrowserSupported ? '#e0f2fe' : '#f1f5f9',
                    color: isBrowserSupported ? '#0284c7' : '#94a3b8',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontSize: '2.2rem',
                    cursor: isBrowserSupported ? 'pointer' : 'not-allowed',
                    transition: 'all 0.2s ease',
                    boxShadow: isBrowserSupported ? '0 4px 6px -1px rgba(2, 132, 199, 0.15)' : 'none',
                  }}
                  title={isBrowserSupported ? 'Click to start recording' : 'Recording unsupported'}
                >
                  🎙️
                </div>

                <div>
                  <div style={{ fontWeight: 700, color: '#0f172a', fontSize: '1rem' }}>
                    {isBrowserSupported ? 'Click to Start Voice Recording' : 'Recording Unavailable'}
                  </div>
                  <div style={{ fontSize: '0.8rem', color: '#64748b', maxWidth: '380px', marginTop: '0.25rem' }}>
                    Speak into your microphone. When finished, MemoraAI will transcribe your reflection into text.
                    Limit: 5 minutes ({MAX_AUDIO_SIZE_BYTES / (1024 * 1024)} MB).
                  </div>
                </div>

                {isBrowserSupported && (
                  <button
                    type="button"
                    onClick={handleStartRecording}
                    style={{
                      backgroundColor: '#0284c7',
                      color: '#ffffff',
                      border: 'none',
                      padding: '0.65rem 1.5rem',
                      borderRadius: '8px',
                      fontWeight: 600,
                      fontSize: '0.9rem',
                      cursor: 'pointer',
                      boxShadow: '0 2px 4px rgba(2, 132, 199, 0.25)',
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.45rem',
                    }}
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
                  backgroundColor: '#f8fafc',
                  border: '1px solid #e2e8f0',
                  borderRadius: '10px',
                  padding: '0.75rem',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: '0.75rem',
                }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontSize: '0.8rem', color: '#64748b' }}>
                  <span>🎧</span>
                  <span>Audio Reference:</span>
                </div>
                <audio controls src={audioUrl} style={{ height: '32px', flex: 1, maxWidth: '340px' }} />
              </div>
            )}

            {/* Provider Disclaimer / Genuine Speech vs Simulation Banner (Requirement 9) */}
            {transcriptResponse && (
              transcriptResponse.isSimulated ? (
                <div
                  style={{
                    backgroundColor: '#fffbeb',
                    border: '1px solid #fde68a',
                    borderRadius: '8px',
                    padding: '0.75rem 1rem',
                    color: '#92400e',
                    fontSize: '0.825rem',
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '0.6rem',
                  }}
                >
                  <span style={{ fontSize: '1.15rem' }}>⚠️</span>
                  <div>
                    <div style={{ fontWeight: 700 }}>Simulated Speech Transcription (Mock Provider)</div>
                    <div style={{ marginTop: '0.2rem', color: '#78350f' }}>
                      This transcript was generated by the development mock provider, not genuine speech
                      recognition. To enable genuine speech transcription, set{' '}
                      <code>STT_PROVIDER=whisper-local</code> in your backend environment.
                    </div>
                  </div>
                </div>
              ) : (
                <div
                  style={{
                    backgroundColor: '#f0fdf4',
                    border: '1px solid #bbf7d0',
                    borderRadius: '8px',
                    padding: '0.75rem 1rem',
                    color: '#166534',
                    fontSize: '0.825rem',
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '0.6rem',
                  }}
                >
                  <span style={{ fontSize: '1.15rem' }}>✓</span>
                  <div>
                    <div style={{ fontWeight: 700 }}>Genuine Speech Recognition</div>
                    <div style={{ marginTop: '0.2rem', color: '#14532d' }}>
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
                backgroundColor: '#f1f5f9',
                border: '1px solid #e2e8f0',
                borderRadius: '8px',
                padding: '0.55rem 0.85rem',
                fontSize: '0.75rem',
                color: '#475569',
              }}
            >
              ℹ️ <strong>Storage Note:</strong> Audio is converted to text memory content. The raw audio file is
              processed in-memory and not stored permanently in the media attachment vault.
            </div>

            {/* Editable Title */}
            <div>
              <label
                style={{
                  display: 'block',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  color: '#334155',
                  marginBottom: '0.35rem',
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
                  padding: '0.6rem 0.85rem',
                  border: '1px solid #cbd5e1',
                  borderRadius: '8px',
                  fontSize: '0.9rem',
                  outline: 'none',
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
                  marginBottom: '0.35rem',
                }}
              >
                <label style={{ fontSize: '0.85rem', fontWeight: 600, color: '#334155' }}>
                  Transcript Content * (Edit before saving)
                </label>
                <span style={{ fontSize: '0.75rem', color: '#0284c7', fontWeight: 600 }}>
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
                  padding: '0.65rem 0.85rem',
                  border: '1px solid #cbd5e1',
                  borderRadius: '8px',
                  fontSize: '0.9rem',
                  outline: 'none',
                  resize: 'vertical',
                  fontFamily: 'inherit',
                  lineHeight: '1.4',
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
                    fontWeight: 600,
                    color: '#334155',
                    marginBottom: '0.25rem',
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
                    padding: '0.55rem 0.75rem',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    fontSize: '0.85rem',
                  }}
                />
              </div>

              <div>
                <label
                  style={{
                    display: 'block',
                    fontSize: '0.8rem',
                    fontWeight: 600,
                    color: '#334155',
                    marginBottom: '0.25rem',
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
                    padding: '0.55rem 0.75rem',
                    border: '1px solid #cbd5e1',
                    borderRadius: '8px',
                    fontSize: '0.85rem',
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
                marginTop: '0.5rem',
                flexWrap: 'wrap',
              }}
            >
              {onTranscriptReady && (
                <button
                  type="button"
                  disabled={isSubmitting || recordingState === 'saved'}
                  onClick={handleUseTranscriptInForm}
                  style={{
                    backgroundColor: '#ffffff',
                    color: '#0284c7',
                    border: '1px solid #0284c7',
                    padding: '0.6rem 1.1rem',
                    borderRadius: '8px',
                    fontWeight: 600,
                    fontSize: '0.875rem',
                    cursor: isSubmitting ? 'not-allowed' : 'pointer',
                  }}
                >
                  Insert into Form Content
                </button>
              )}

              <button
                type="button"
                disabled={isSubmitting || recordingState === 'saved'}
                onClick={onClose}
                style={{
                  backgroundColor: '#ffffff',
                  color: '#475569',
                  border: '1px solid #cbd5e1',
                  padding: '0.6rem 1.1rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.875rem',
                  cursor: isSubmitting ? 'not-allowed' : 'pointer',
                }}
              >
                Cancel
              </button>

              <button
                type="submit"
                disabled={isSubmitting || recordingState === 'saved'}
                style={{
                  backgroundColor: isSubmitting || recordingState === 'saved' ? '#94a3b8' : '#0284c7',
                  color: '#ffffff',
                  border: 'none',
                  padding: '0.6rem 1.35rem',
                  borderRadius: '8px',
                  fontWeight: 600,
                  fontSize: '0.875rem',
                  cursor: isSubmitting || recordingState === 'saved' ? 'not-allowed' : 'pointer',
                  boxShadow: '0 2px 4px rgba(2, 132, 199, 0.25)',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.4rem',
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
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
