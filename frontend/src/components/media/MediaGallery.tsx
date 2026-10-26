'use client';

import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  deleteMemoryMediaApi,
  formatFileSize,
  getMemoryMediaApi,
  MAX_MEDIA_PER_MEMORY,
  uploadMemoryMediaApi,
  validateMediaFiles,
} from '../../lib/media';
import { MemoryMedia } from '../../types/media';
import AuthenticatedImage from './AuthenticatedImage';

interface MediaGalleryProps {
  memoryId: string;
  onMediaCountChange?: (count: number) => void;
}

export default function MediaGallery({ memoryId, onMediaCountChange }: MediaGalleryProps) {
  const [mediaList, setMediaList] = useState<MemoryMedia[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isUploading, setIsUploading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  // Lightbox Preview State
  const [activePreviewMedia, setActivePreviewMedia] = useState<MemoryMedia | null>(null);

  // Deletion Confirmation State
  const [deletingMedia, setDeletingMedia] = useState<MemoryMedia | null>(null);
  const [isDeleting, setIsDeleting] = useState<boolean>(false);

  // Drag and Drop State
  const [isDragging, setIsDragging] = useState<boolean>(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const fetchMedia = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await getMemoryMediaApi(memoryId);
      setMediaList(data);
      if (onMediaCountChange) {
        onMediaCountChange(data.length);
      }
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to load attached media.');
      }
    } finally {
      setIsLoading(false);
    }
  }, [memoryId, onMediaCountChange]);

  useEffect(() => {
    let isCurrent = true;

    setIsLoading(true);
    setError(null);
    setSuccessMsg(null);
    setActivePreviewMedia(null);
    setDeletingMedia(null);

    getMemoryMediaApi(memoryId)
      .then((data) => {
        if (!isCurrent) return;
        setMediaList(data);
        setIsLoading(false);
        if (onMediaCountChange) {
          onMediaCountChange(data.length);
        }
      })
      .catch((err: unknown) => {
        if (!isCurrent) return;
        if (err instanceof Error) {
          setError(err.message);
        } else {
          setError('Failed to load attached media.');
        }
        setIsLoading(false);
      });

    return () => {
      isCurrent = false;
    };
  }, [memoryId, onMediaCountChange]);

  const handleFilesSelected = async (fileList: FileList | null) => {
    if (!fileList || fileList.length === 0) return;
    setError(null);
    setSuccessMsg(null);

    const filesArray = Array.from(fileList);
    const validation = validateMediaFiles(filesArray, mediaList.length);

    if (!validation.valid) {
      setError(validation.error || 'Selected files failed validation.');
      if (fileInputRef.current) fileInputRef.current.value = '';
      return;
    }

    setIsUploading(true);
    try {
      const uploaded = await uploadMemoryMediaApi(memoryId, filesArray);
      const updatedList = [...mediaList, ...uploaded];
      setMediaList(updatedList);
      setSuccessMsg(
        `Successfully uploaded ${uploaded.length} image${uploaded.length > 1 ? 's' : ''}.`
      );
      if (onMediaCountChange) {
        onMediaCountChange(updatedList.length);
      }
      if (fileInputRef.current) fileInputRef.current.value = '';
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Upload failed. Please check file format and network.');
      }
    } finally {
      setIsUploading(false);
    }
  };

  const handleDeleteConfirm = async () => {
    if (!deletingMedia) return;
    setIsDeleting(true);
    setError(null);
    try {
      await deleteMemoryMediaApi(memoryId, deletingMedia.id);
      const remaining = mediaList.filter((m) => m.id !== deletingMedia.id);
      setMediaList(remaining);
      if (onMediaCountChange) {
        onMediaCountChange(remaining.length);
      }
      if (activePreviewMedia?.id === deletingMedia.id) {
        setActivePreviewMedia(null);
      }
      setDeletingMedia(null);
      setSuccessMsg(`Image "${deletingMedia.fileName}" removed.`);
    } catch (err: unknown) {
      if (err instanceof Error) {
        setError(err.message);
      } else {
        setError('Failed to delete image.');
      }
    } finally {
      setIsDeleting(false);
    }
  };

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    if (mediaList.length < MAX_MEDIA_PER_MEMORY && !isUploading) {
      setIsDragging(true);
    }
  };

  const handleDragLeave = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    if (mediaList.length >= MAX_MEDIA_PER_MEMORY || isUploading) return;
    handleFilesSelected(e.dataTransfer.files);
  };

  const isLimitReached = mediaList.length >= MAX_MEDIA_PER_MEMORY;

  return (
    <div style={{ marginTop: '1.5rem', borderTop: '1px solid var(--color-border)', paddingTop: '1.25rem' }}>
      {/* Header with Attachment Counter */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '0.75rem',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <span style={{ fontSize: '1rem', fontWeight: 700, color: 'var(--color-text)' }}>
            Attached Images
          </span>
          <span
            style={{
              fontSize: '0.75rem',
              fontWeight: 700,
              backgroundColor: isLimitReached ? 'var(--color-error-background)' : 'var(--color-accent-soft)',
              color: isLimitReached ? 'var(--color-error)' : 'var(--primary-hover)',
              padding: '0.15rem 0.55rem',
              borderRadius: '9999px',
            }}
          >
            {mediaList.length} / {MAX_MEDIA_PER_MEMORY}
          </span>
        </div>

        {isLimitReached && (
          <span style={{ fontSize: '0.75rem', color: 'var(--color-secondary-text)', fontWeight: 500 }}>
            Maximum image limit reached
          </span>
        )}
      </div>

      {/* Messages */}
      {error && (
        <div
          style={{
            backgroundColor: 'var(--color-error-background)',
            border: '1px solid var(--color-error-border)',
            color: 'var(--color-error)',
            padding: '0.65rem 0.9rem',
            borderRadius: '8px',
            fontSize: '0.85rem',
            marginBottom: '1rem',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <span>{error}</span>
          <button
            onClick={() => setError(null)}
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--color-error)',
              cursor: 'pointer',
              fontWeight: 'bold',
            }}
          >
            ✕
          </button>
        </div>
      )}

      {successMsg && (
        <div
          style={{
            backgroundColor: 'var(--color-success-background)',
            border: '1px solid var(--color-success-border)',
            color: 'var(--color-success)',
            padding: '0.65rem 0.9rem',
            borderRadius: '8px',
            fontSize: '0.85rem',
            marginBottom: '1rem',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
          }}
        >
          <span>{successMsg}</span>
          <button
            onClick={() => setSuccessMsg(null)}
            style={{
              background: 'none',
              border: 'none',
              color: 'var(--color-success)',
              cursor: 'pointer',
              fontWeight: 'bold',
            }}
          >
            ✕
          </button>
        </div>
      )}

      {/* Loading Gallery State */}
      {isLoading ? (
        <div
          style={{
            padding: '2rem',
            textAlign: 'center',
            color: 'var(--color-secondary-text)',
            fontSize: '0.9rem',
            backgroundColor: 'var(--color-background)',
            borderRadius: '10px',
            border: '1px solid var(--color-border)',
          }}
        >
          Loading attachments...
        </div>
      ) : (
        <>
          {/* Empty State */}
          {mediaList.length === 0 ? (
            <div
              style={{
                padding: '1.5rem',
                textAlign: 'center',
                color: 'var(--color-secondary-text)',
                backgroundColor: 'var(--color-background)',
                borderRadius: '10px',
                border: '1px dashed var(--color-border)',
                fontSize: '0.85rem',
                marginBottom: '1rem',
              }}
            >
              No images attached to this memory yet. Upload JPEG, PNG, or WEBP photos below.
            </div>
          ) : (
            /* Media Grid */
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(130px, 1fr))',
                gap: '0.75rem',
                marginBottom: '1.25rem',
              }}
            >
              {mediaList.map((media) => (
                <div
                  key={media.id}
                  style={{
                    backgroundColor: 'var(--color-surface)',
                    border: '1px solid var(--color-border)',
                    borderRadius: '10px',
                    overflow: 'hidden',
                    display: 'flex',
                    flexDirection: 'column',
                    boxShadow: '0 1px 3px rgba(var(--color-text-rgb), 0.05)',
                    position: 'relative',
                  }}
                >
                  {/* Thumbnail */}
                  <div
                    style={{
                      height: '110px',
                      width: '100%',
                      backgroundColor: 'var(--color-background)',
                      position: 'relative',
                      overflow: 'hidden',
                    }}
                  >
                    <AuthenticatedImage
                      memoryId={memoryId}
                      mediaId={media.id}
                      alt={media.fileName}
                      onClick={() => setActivePreviewMedia(media)}
                    />
                  </div>

                  {/* Metadata & Actions */}
                  <div style={{ padding: '0.5rem', flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                    <div
                      title={media.fileName}
                      style={{
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        color: 'var(--color-text)',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {media.fileName}
                    </div>

                    <div
                      style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                        marginTop: '0.35rem',
                      }}
                    >
                      <span style={{ fontSize: '0.7rem', color: 'var(--color-secondary-text)' }}>
                        {formatFileSize(media.fileSize)}
                      </span>
                      <button
                        onClick={() => setDeletingMedia(media)}
                        title="Delete image"
                        style={{
                          backgroundColor: 'var(--color-error-background)',
                          color: 'var(--color-error)',
                          border: 'none',
                          borderRadius: '4px',
                          padding: '0.2rem 0.45rem',
                          fontSize: '0.7rem',
                          fontWeight: 600,
                          cursor: 'pointer',
                        }}
                      >
                        Delete
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {/* Upload Drop Zone / Input */}
          {!isLimitReached && (
            <div
              onDragOver={handleDragOver}
              onDragLeave={handleDragLeave}
              onDrop={handleDrop}
              onClick={() => !isUploading && fileInputRef.current?.click()}
              style={{
                border: isDragging ? '2px dashed var(--color-primary)' : '2px dashed var(--color-border)',
                backgroundColor: isDragging ? 'var(--color-accent-soft)' : 'var(--color-background)',
                borderRadius: '12px',
                padding: '1.25rem',
                textAlign: 'center',
                cursor: isUploading ? 'not-allowed' : 'pointer',
                transition: 'all 0.15s ease',
              }}
            >
              <input
                ref={fileInputRef}
                type="file"
                multiple
                accept="image/jpeg,image/png,image/webp,.jpg,.jpeg,.png,.webp"
                disabled={isUploading}
                onChange={(e) => handleFilesSelected(e.target.files)}
                style={{ display: 'none' }}
              />

              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '0.35rem' }}>
                <span style={{ fontSize: '1.4rem' }}>📷</span>
                <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--color-text)' }}>
                  {isUploading ? 'Uploading image(s)...' : 'Click or drag images to upload'}
                </span>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-secondary-text)' }}>
                  JPEG, PNG, or WEBP up to 10 MB each ({MAX_MEDIA_PER_MEMORY - mediaList.length} slot
                  {MAX_MEDIA_PER_MEMORY - mediaList.length > 1 ? 's' : ''} remaining)
                </span>
              </div>
            </div>
          )}
        </>
      )}

      {/* LIGHTBOX FULLSCREEN MODAL */}
      {activePreviewMedia && (
        <div
          onClick={() => setActivePreviewMedia(null)}
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(var(--color-text-rgb), 0.85)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '1.5rem',
            zIndex: 1000,
          }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              maxWidth: '90vw',
              maxHeight: '85vh',
              display: 'flex',
              flexDirection: 'column',
              backgroundColor: 'var(--color-surface)',
              borderRadius: '14px',
              overflow: 'hidden',
              boxShadow: '0 25px 50px -12px rgba(var(--color-text-rgb), 0.25)',
            }}
          >
            {/* Lightbox Header */}
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                padding: '0.75rem 1.25rem',
                borderBottom: '1px solid var(--color-border)',
              }}
            >
              <div>
                <span style={{ fontSize: '0.9rem', fontWeight: 700, color: 'var(--color-text)' }}>
                  {activePreviewMedia.fileName}
                </span>
                <span style={{ fontSize: '0.75rem', color: 'var(--color-secondary-text)', marginLeft: '0.5rem' }}>
                  ({formatFileSize(activePreviewMedia.fileSize)})
                </span>
              </div>
              <button
                onClick={() => setActivePreviewMedia(null)}
                style={{
                  backgroundColor: 'transparent',
                  border: 'none',
                  fontSize: '1.2rem',
                  cursor: 'pointer',
                  color: 'var(--color-secondary-text)',
                  lineHeight: 1,
                }}
              >
                ✕
              </button>
            </div>

            {/* Lightbox Image Viewport */}
            <div
              style={{
                padding: '0.75rem',
                backgroundColor: 'var(--color-text)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                maxHeight: '75vh',
                overflow: 'hidden',
              }}
            >
              <AuthenticatedImage
                memoryId={memoryId}
                mediaId={activePreviewMedia.id}
                alt={activePreviewMedia.fileName}
                style={{
                  maxHeight: '70vh',
                  maxWidth: '85vw',
                  objectFit: 'contain',
                  borderRadius: '4px',
                }}
              />
            </div>
          </div>
        </div>
      )}

      {/* DELETE CONFIRMATION MODAL */}
      {deletingMedia && (
        <div
          onClick={() => !isDeleting && setDeletingMedia(null)}
          style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(var(--color-text-rgb), 0.6)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '1rem',
            zIndex: 1100,
          }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              backgroundColor: 'var(--color-surface)',
              borderRadius: '14px',
              maxWidth: '420px',
              width: '100%',
              padding: '1.5rem',
              boxShadow: '0 20px 25px -5px rgba(var(--color-text-rgb), 0.1)',
            }}
          >
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--color-text)', margin: '0 0 0.5rem 0' }}>
              Delete Image Attachment?
            </h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--color-secondary-text)', lineHeight: 1.5, margin: '0 0 1.25rem 0' }}>
              Are you sure you want to delete <strong>{deletingMedia.fileName}</strong>? This action cannot
              be undone and will permanently remove the image from storage.
            </p>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
              <button
                type="button"
                disabled={isDeleting}
                onClick={() => setDeletingMedia(null)}
                style={{
                  backgroundColor: 'var(--color-background)',
                  color: 'var(--color-text)',
                  border: '1px solid var(--color-border)',
                  borderRadius: '8px',
                  padding: '0.5rem 1rem',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  cursor: isDeleting ? 'not-allowed' : 'pointer',
                }}
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={isDeleting}
                onClick={handleDeleteConfirm}
                style={{
                  backgroundColor: 'var(--color-error)',
                  color: 'var(--color-surface)',
                  border: 'none',
                  borderRadius: '8px',
                  padding: '0.5rem 1rem',
                  fontSize: '0.85rem',
                  fontWeight: 600,
                  cursor: isDeleting ? 'not-allowed' : 'pointer',
                }}
              >
                {isDeleting ? 'Deleting...' : 'Confirm Delete'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
