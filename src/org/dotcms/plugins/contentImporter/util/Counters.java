package org.dotcms.plugins.contentImporter.util;

public class Counters {
    public int newContentCounter = 0;
    public int contentToUpdateCounter = 0;
    public int contentCreated = 0;
    public int contentUpdated = 0;
    public int contentUpdatedDuplicated = 0;
    /**
     * @return the newContentCounter
     */
    public int getNewContentCounter() {
        return newContentCounter;
    }
    /**
     * @param newContentCounter the newContentCounter to set
     */
    public void setNewContentCounter(int newContentCounter) {
        this.newContentCounter = newContentCounter;
    }
    /**
     * @return the contentToUpdateCounter
     */
    public int getContentToUpdateCounter() {
        return contentToUpdateCounter;
    }
    /**
     * @param contentToUpdateCounter the contentToUpdateCounter to set
     */
    public void setContentToUpdateCounter(int contentToUpdateCounter) {
        this.contentToUpdateCounter = contentToUpdateCounter;
    }
    /**
     * @return the contentCreated
     */
    public int getContentCreated() {
        return contentCreated;
    }
    /**
     * @param contentCreated the contentCreated to set
     */
    public void setContentCreated(int contentCreated) {
        this.contentCreated = contentCreated;
    }
    /**
     * @return the contentUpdated
     */
    public int getContentUpdated() {
        return contentUpdated;
    }
    /**
     * @param contentUpdated the contentUpdated to set
     */
    public void setContentUpdated(int contentUpdated) {
        this.contentUpdated = contentUpdated;
    }
    /**
     * @return the contentUpdatedDuplicated
     */
    public int getContentUpdatedDuplicated() {
        return contentUpdatedDuplicated;
    }
    /**
     * @param contentUpdatedDuplicated the contentUpdatedDuplicated to set
     */
    public void setContentUpdatedDuplicated(int contentUpdatedDuplicated) {
        this.contentUpdatedDuplicated = contentUpdatedDuplicated;
    }
}
