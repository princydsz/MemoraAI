'use client';

import React, { useEffect, useState } from 'react';
import { fetchMediaBlobApi, formatFileSize } from '../../lib/media';

export interface AuthenticatedAudioPlayerProps {
  memoryId: string;
  mediaId: string;
  fileName?: string;
  fileSize?: number;
  autoPlay?: boolean;
  style?: React.CSSProperties;
}

export default function AuthenticatedAudioPlayer({
  memoryId,
  mediaId,
  fileName,
  fileSize,
  autoPlay = false,
  style,
}: AuthenticatedAudioPlayerProps) {
  const [audioUrl, setAudioUrl] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [hasError, setHasError] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [retryKey, setRetryKey] = useState<number>(0);

  useEffect(() => {
    let isCurrent = true;
    let createdUrl: string | null = null;

    setIsLoading(true);
    setHasError(false);
    setErrorMessage(null);

    fetchMediaBlobApi(memoryId, mediaId)
      .then((blob) => {
        if (!isCurrent) return;
        createdUrl = URL.createObjectURL(blob);
        setAudioUrl(createdUrl);
        setIsLoading(false);
      })
      .catch((err: unknown) => {
        if (!isCurrent) return;
        const msg = err instanceof Error ? err.message : 'Failed to load audio.';
        setErrorMessage(msg);
        setHasError(true);
        setIsLoading(false);
      });

    return () => {
      isCurrent = false;
      if (createdUrl) {
        URL.revokeObjectURL(createdUrl);
      }
    };
  }, [memoryId, mediaId, retryKey]);

  if (isLoading) {
    return (
      <div
        style={{
          width: '100%',
          padding: '0.85rem 1rem',
          backgroundColor: 'var(--surface-muted)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius-sm)',
          display: 'flex',
          alignItems: 'center',
          gap: '0.75rem',
          color: 'var(--text-muted)',
          fontSize: '0.85rem',
          ...style,
        }}
      >
        <span
          style={{
            width: '16px',
            height: '16px',
            border: '2px solid rgba(182, 90, 70, 0.2)',
            borderTop: '2px solid var(--primary)',
            borderRadius: '50%',
            animation: 'spin 1s linear infinite',
            flexShrink: 0,
          }}
        />
        <span>Loading voice recording...</span>
      </div>
    );
  }

  if (hasError || !audioUrl) {
    return (
      <div
        style={{
          width: '100%',
          padding: '0.75rem 1rem',
          backgroundColor: 'var(--danger-background)',
          border: '1px solid var(--danger-border)',
          borderRadius: 'var(--radius-sm)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          color: 'var(--danger)',
          fontSize: '0.82rem',
          ...style,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span>⚠️</span>
          <span style={{ fontWeight: 500 }}>{errorMessage || 'Voice audio unavailable'}</span>
        </div>
        <button
          type="button"
          onClick={() => setRetryKey((prev) => prev + 1)}
          style={{
            background: 'white',
            border: '1px solid var(--danger-border)',
            borderRadius: '999px',
            color: 'var(--danger)',
            padding: '0.25rem 0.75rem',
            fontSize: '0.75rem',
            fontWeight: 700,
            cursor: 'pointer',
          }}
        >
          Retry
        </button>
      </div>
    );
  }

  return (
    <div
      style={{
        width: '100%',
        backgroundColor: 'var(--surface-muted)',
        border: '1px solid var(--border)',
        borderRadius: 'var(--radius-sm)',
        padding: '0.85rem 1rem',
        display: 'flex',
        flexDirection: 'column',
        gap: '0.6rem',
        boxShadow: 'var(--shadow-card)',
        ...style,
      }}
    >
      {(fileName || fileSize !== undefined) && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            fontSize: '0.82rem',
            color: 'var(--text)',
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '0.45rem',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              fontWeight: 700,
            }}
            title={fileName}
          >
            <span
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: '1.4rem',
                height: '1.4rem',
                borderRadius: '50%',
                backgroundColor: 'rgba(var(--accent-rgb), 0.12)',
                color: 'var(--accent-strong)',
                fontSize: '0.85rem',
                flexShrink: 0,
              }}
            >
              🎙️
            </span>
            <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {fileName || 'Voice Recording'}
            </span>
          </div>
          {fileSize !== undefined && (
            <span
              style={{
                fontSize: '0.72rem',
                color: 'var(--text-muted)',
                marginLeft: '0.5rem',
                fontWeight: 600,
                flexShrink: 0,
              }}
            >
              {formatFileSize(fileSize)}
            </span>
          )}
        </div>
      )}

      <audio
        controls
        src={audioUrl}
        autoPlay={autoPlay}
        preload="metadata"
        style={{
          width: '100%',
          height: '38px',
          borderRadius: '8px',
          outline: 'none',
        }}
      >
        Your browser does not support the audio element.
      </audio>
    </div>
  );
}
