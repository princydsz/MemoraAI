'use client';

import React, { useEffect, useState } from 'react';
import { fetchMediaBlobApi } from '../../lib/media';

interface AuthenticatedImageProps {
  memoryId: string;
  mediaId: string;
  alt: string;
  style?: React.CSSProperties;
  onClick?: () => void;
  className?: string;
}

export default function AuthenticatedImage({
  memoryId,
  mediaId,
  alt,
  style,
  onClick,
}: AuthenticatedImageProps) {
  const [objectUrl, setObjectUrl] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [hasError, setHasError] = useState<boolean>(false);

  useEffect(() => {
    let isCurrent = true;
    let createdUrl: string | null = null;

    setIsLoading(true);
    setHasError(false);

    fetchMediaBlobApi(memoryId, mediaId)
      .then((blob) => {
        if (!isCurrent) return;
        createdUrl = URL.createObjectURL(blob);
        setObjectUrl(createdUrl);
        setIsLoading(false);
      })
      .catch(() => {
        if (!isCurrent) return;
        setHasError(true);
        setIsLoading(false);
      });

    return () => {
      isCurrent = false;
      if (createdUrl) {
        URL.revokeObjectURL(createdUrl);
      }
    };
  }, [memoryId, mediaId]);

  if (isLoading) {
    return (
      <div
        style={{
          width: '100%',
          height: '100%',
          minHeight: '120px',
          backgroundColor: 'var(--color-background)',
          borderRadius: '8px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--color-disabled)',
          fontSize: '0.8rem',
          animation: 'pulse 1.5s infinite ease-in-out',
          ...style,
        }}
      >
        <span>Loading...</span>
      </div>
    );
  }

  if (hasError || !objectUrl) {
    return (
      <div
        style={{
          width: '100%',
          height: '100%',
          minHeight: '120px',
          backgroundColor: 'var(--color-error-background)',
          border: '1px solid var(--color-error-border)',
          borderRadius: '8px',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'var(--color-error)',
          fontSize: '0.75rem',
          padding: '0.5rem',
          textAlign: 'center',
          ...style,
        }}
      >
        <span style={{ fontSize: '1.2rem', marginBottom: '0.25rem' }}>⚠️</span>
        <span>Image unavailable</span>
      </div>
    );
  }

  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img
      src={objectUrl}
      alt={alt}
      onClick={onClick}
      style={{
        width: '100%',
        height: '100%',
        objectFit: 'cover',
        borderRadius: '8px',
        cursor: onClick ? 'pointer' : 'default',
        transition: 'transform 0.15s ease',
        ...style,
      }}
    />
  );
}
