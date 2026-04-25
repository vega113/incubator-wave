package org.waveprotocol.box.j2cl.attachment;

public final class J2clAttachmentMetadata {
  private final String attachmentId;
  private final String waveRef;
  private final String fileName;
  private final String mimeType;
  private final long size;
  private final String creator;
  private final String attachmentUrl;
  private final String thumbnailUrl;
  private final ImageMetadata imageMetadata;
  private final ImageMetadata thumbnailMetadata;
  private final boolean malware;

  public J2clAttachmentMetadata(
      String attachmentId,
      String waveRef,
      String fileName,
      String mimeType,
      long size,
      String creator,
      String attachmentUrl,
      String thumbnailUrl,
      ImageMetadata imageMetadata,
      ImageMetadata thumbnailMetadata,
      boolean malware) {
    this.attachmentId = attachmentId;
    this.waveRef = waveRef;
    this.fileName = fileName;
    this.mimeType = mimeType;
    this.size = size;
    this.creator = creator;
    this.attachmentUrl = attachmentUrl;
    this.thumbnailUrl = thumbnailUrl;
    this.imageMetadata = imageMetadata;
    this.thumbnailMetadata = thumbnailMetadata;
    this.malware = malware;
  }

  public String getAttachmentId() {
    return attachmentId;
  }

  public String getWaveRef() {
    return waveRef;
  }

  public String getFileName() {
    return fileName;
  }

  public String getMimeType() {
    return mimeType;
  }

  public long getSize() {
    return size;
  }

  public String getCreator() {
    return creator;
  }

  public String getAttachmentUrl() {
    return attachmentUrl;
  }

  public String getThumbnailUrl() {
    return thumbnailUrl;
  }

  public ImageMetadata getImageMetadata() {
    return imageMetadata;
  }

  public ImageMetadata getThumbnailMetadata() {
    return thumbnailMetadata;
  }

  public boolean isMalware() {
    return malware;
  }

  public static final class ImageMetadata {
    private final int width;
    private final int height;

    public ImageMetadata(int width, int height) {
      this.width = width;
      this.height = height;
    }

    public int getWidth() {
      return width;
    }

    public int getHeight() {
      return height;
    }
  }
}
