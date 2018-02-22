package org.cru.contentscoring.core.models;

import java.math.BigDecimal;
import java.util.Map;

public class ContentScoreUpdateRequest {
    private String contentId;
    private Map<ScoreType, BigDecimal> contentScores;

    public String getContentId() {
        return contentId;
    }

    public void setContentId(final String contentId) {
        this.contentId = contentId;
    }

    public Map<ScoreType, BigDecimal> getContentScores() {
        return contentScores;
    }

    public void setContentScores(final Map<ScoreType, BigDecimal> contentScores) {
        this.contentScores = contentScores;
    }
}
