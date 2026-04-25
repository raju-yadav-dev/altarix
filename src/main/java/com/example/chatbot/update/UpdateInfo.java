package com.example.chatbot.update;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

/**
 * Simple DTO for the update API payload.
 */
public final class UpdateInfo {
    @SerializedName("version")
    private String version;

    @SerializedName("download_url")
    private String downloadUrl;

    @SerializedName("release_notes")
    private String releaseNotes;

    @SerializedName("is_mandatory")
    private boolean mandatory;

    @SerializedName("updates")
    private List<UpdateRow> updates;

    public String getVersion() {
        return version == null ? "" : version.trim();
    }

    public String getDownloadUrl() {
        return downloadUrl == null ? "" : downloadUrl.trim();
    }

    public String getReleaseNotes() {
        return releaseNotes == null ? "" : releaseNotes.trim();
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public List<UpdateRow> getUpdates() {
        return updates == null ? Collections.emptyList() : updates;
    }

    public boolean isValid() {
        if (getVersion().isEmpty()) {
            return false;
        }
        if (!getDownloadUrl().isEmpty()) {
            return true;
        }
        return getUpdates().stream().anyMatch(row -> row != null && !row.getDownloadUrl().isEmpty());
    }

    public static final class UpdateRow {
        @SerializedName("version")
        private String version;

        @SerializedName("download_url")
        private String downloadUrl;

        @SerializedName("type")
        private String type;

        public String getVersion() {
            return version == null ? "" : version.trim();
        }

        public String getDownloadUrl() {
            return downloadUrl == null ? "" : downloadUrl.trim();
        }

        public String getType() {
            return type == null ? "" : type.trim().toLowerCase();
        }
    }
}
