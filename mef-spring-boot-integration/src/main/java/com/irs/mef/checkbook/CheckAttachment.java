package com.irs.mef.checkbook;

/** File carried by a digital check. Jackson writes {@code content} as base64, the form Checkbook expects. */
public record CheckAttachment(String filename, byte[] content) {

    static final int MAX_FILENAME_LENGTH = 128;

    public CheckAttachment {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename must not be blank");
        }
        if (filename.length() > MAX_FILENAME_LENGTH) {
            throw new IllegalArgumentException("filename must be at most " + MAX_FILENAME_LENGTH + " characters");
        }
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("content must not be empty");
        }
    }
}
