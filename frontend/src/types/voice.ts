export interface TranscribeResponse {
  transcript: string;
  detectedLanguage: string;
  durationSeconds: number;
  provider: string;
  isSimulated: boolean;
}

export interface AudioValidationResult {
  valid: boolean;
  error?: string;
}

export type VoiceRecordingState =
  | 'idle'
  | 'recording'
  | 'recorded'
  | 'transcribing'
  | 'transcribed'
  | 'saving'
  | 'saved'
  | 'error';
