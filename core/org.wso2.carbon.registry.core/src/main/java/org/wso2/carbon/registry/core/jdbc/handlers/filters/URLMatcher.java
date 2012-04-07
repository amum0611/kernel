/*
 * Copyright (c) 2008, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.registry.core.jdbc.handlers.filters;

import org.wso2.carbon.registry.core.exceptions.RegistryException;
import org.wso2.carbon.registry.core.jdbc.handlers.RequestContext;

/**
 * This is a built-in Filter implementation to match against the URL (path) of the resources. This
 * can match different URLs for different handler operations. URLs to match for necessary handler
 * operations can be given as regular expressions. If a URL for particular operation is not
 * specified, it will always evaluate to false.
 * <p/>
 * Handler authors can use this Filter in their configurations if the filtering requirement is only
 * to match against URLs of the resources.
 */
public class URLMatcher extends Filter {

    /**
     * URLs to match against resource path for handler operations. They should be in the form of
     * regular expressions.
     */
    private String getPattern;
    private String executeQueryPattern;
    private String putPattern;
    private String importPattern;
    private String deletePattern;
    private String putChildPattern;
    private String importChildPattern;
    private String invokeAspectPattern;
    private String movePattern;
    private String copyPattern;
    private String renamePattern;
    private String createLinkPattern;
    private String removeLinkPattern;
    private String resourceExistsPattern;
    private String getRegistryContextPattern;
    private String addAssociationPattern;
    private String removeAssociationPattern;
    private String getAllAssociationsPattern;
    private String getAssociationsPattern;
    private String applyTagPattern;
    private String getTagsPattern;
    private String removeTagPattern;
    private String addCommentPattern;
    private String editCommentPattern;
    private String removeCommentPattern;
    private String getCommentsPattern;
    private String rateResourcePattern;
    private String getAverageRatingPattern;
    private String getRatingPattern;
    private String createVersionPattern;
    private String getVersionsPattern;
    private String restoreVersionPattern;
    private String dumpPattern;
    private String restorePattern;

    public int hashCode() {
        return getEqualsComparator().hashCode();
    }

    // Method to generate a unique string that can be used to compare two objects of the same type
    // for equality.
    private String getEqualsComparator() {
        StringBuffer sb = new StringBuffer();
        sb.append(getClass().getName());
        sb.append("|");
        sb.append(getPattern);
        sb.append("|");
        sb.append(executeQueryPattern);
        sb.append("|");
        sb.append(putPattern);
        sb.append("|");
        sb.append(importPattern);
        sb.append("|");
        sb.append(deletePattern);
        sb.append("|");
        sb.append(putChildPattern);
        sb.append("|");
        sb.append(importChildPattern);
        sb.append("|");
        sb.append(invokeAspectPattern);
        sb.append("|");
        sb.append(movePattern);
        sb.append("|");
        sb.append(copyPattern);
        sb.append("|");
        sb.append(renamePattern);
        sb.append("|");
        sb.append(createLinkPattern);
        sb.append("|");
        sb.append(removeLinkPattern);
        sb.append("|");
        sb.append(resourceExistsPattern);
        sb.append("|");
        sb.append(getRegistryContextPattern);
        sb.append("|");
        sb.append(addAssociationPattern);
        sb.append("|");
        sb.append(removeAssociationPattern);
        sb.append("|");
        sb.append(getAllAssociationsPattern);
        sb.append("|");
        sb.append(getAssociationsPattern);
        sb.append("|");
        sb.append(applyTagPattern);
        sb.append("|");
        sb.append(getTagsPattern);
        sb.append("|");
        sb.append(removeTagPattern);
        sb.append("|");
        sb.append(addCommentPattern);
        sb.append("|");
        sb.append(editCommentPattern);
        sb.append("|");
        sb.append(removeCommentPattern);
        sb.append("|");
        sb.append(getCommentsPattern);
        sb.append("|");
        sb.append(rateResourcePattern);
        sb.append("|");
        sb.append(getAverageRatingPattern);
        sb.append("|");
        sb.append(getRatingPattern);
        sb.append("|");
        sb.append(createVersionPattern);
        sb.append("|");
        sb.append(getVersionsPattern);
        sb.append("|");
        sb.append(restoreVersionPattern);
        sb.append("|");
        sb.append(dumpPattern);
        sb.append("|");
        sb.append(restorePattern);
        sb.append("|");
        sb.append(invert);
        return sb.toString();
    }

    /**
     * Compares this MediaTypeMatcher to the specified object.  The result is {@code true} if and
     * only if the argument is not {@code null} and is a {@code MediaTypeMatcher} object that
     * contains the same values for the fields as this object.
     *
     * @param other The object to compare the {@code MediaTypeMatcher} against
     *
     * @return {@code true} if the given object represents a {@code MediaTypeMatcher} equivalent to
     *         this instance, {@code false} otherwise.
     */
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (other == null) {
            return false;
        }
        if (other instanceof URLMatcher) {
            URLMatcher otherURLMatcher = (URLMatcher) other;
            return (getEqualsComparator().equals(otherURLMatcher.getEqualsComparator()));
        }
        return false;
    }

    public boolean handleGet(RequestContext requestContext) throws RegistryException {
        return getPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(getPattern));
    }

    public boolean handleExecuteQuery(RequestContext requestContext) throws RegistryException {
        return executeQueryPattern != null && requestContext.getResourcePath() != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(executeQueryPattern));
    }

    public boolean handlePut(RequestContext requestContext) throws RegistryException {
        return putPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(putPattern));
    }

    public boolean handleImportResource(RequestContext requestContext) throws RegistryException {
        return importPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(importPattern));
    }

    public boolean handleDelete(RequestContext requestContext) throws RegistryException {
        return deletePattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(deletePattern));
    }

    public boolean handlePutChild(RequestContext requestContext) throws RegistryException {
        return putChildPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(putChildPattern));
    }

    public boolean handleImportChild(RequestContext requestContext) throws RegistryException {
        return importChildPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(importChildPattern));
    }

    public boolean handleInvokeAspect(RequestContext requestContext) throws RegistryException {
        return invokeAspectPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(invokeAspectPattern));
    }

    public boolean handleCopy(RequestContext requestContext) throws RegistryException {
        return copyPattern != null && ((invert !=
                requestContext.getSourcePath().matches(copyPattern)) || (invert !=
                requestContext.getTargetPath().matches(copyPattern)));
    }

    public boolean handleMove(RequestContext requestContext) throws RegistryException {
        return movePattern != null && ((invert !=
                requestContext.getSourcePath().matches(movePattern)) || (invert !=
                requestContext.getTargetPath().matches(movePattern)));
    }

    public boolean handleRename(RequestContext requestContext) throws RegistryException {
        return renamePattern != null && (invert !=
                requestContext.getSourcePath().matches(renamePattern));
    }

    public boolean handleCreateLink(RequestContext requestContext) throws RegistryException {
        return createLinkPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(createLinkPattern));
    }

    public boolean handleRemoveLink(RequestContext requestContext) throws RegistryException {
        return removeLinkPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(removeLinkPattern));
    }

    public boolean handleResourceExists(RequestContext requestContext) throws RegistryException {
        return resourceExistsPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(resourceExistsPattern));
    }

    public boolean handleGetRegistryContext(RequestContext requestContext) {
        return getRegistryContextPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath()
                        .matches(getRegistryContextPattern));
    }

    public boolean handleAddAssociation(RequestContext requestContext) throws RegistryException {
        return addAssociationPattern != null && (invert !=
                requestContext.getSourcePath().matches(addAssociationPattern));
    }

    public boolean handleRemoveAssociation(RequestContext requestContext) throws RegistryException {
        return removeAssociationPattern != null && (invert !=
                requestContext.getSourcePath().matches(removeAssociationPattern));
    }

    public boolean handleGetAllAssociations(RequestContext requestContext)
            throws RegistryException {
        return getAllAssociationsPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath()
                        .matches(getAllAssociationsPattern));
    }

    public boolean handleGetAssociations(RequestContext requestContext) throws RegistryException {
        return getAssociationsPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(getAssociationsPattern));
    }

    public boolean handleApplyTag(RequestContext requestContext) throws RegistryException {
        return applyTagPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(applyTagPattern));
    }

    public boolean handleGetTags(RequestContext requestContext) throws RegistryException {
        return getTagsPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(getTagsPattern));
    }

    public boolean handleRemoveTag(RequestContext requestContext) throws RegistryException {
        return removeTagPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(removeTagPattern));
    }

    public boolean handleAddComment(RequestContext requestContext) throws RegistryException {
        return addCommentPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(addCommentPattern));
    }

    public boolean handleEditComment(RequestContext requestContext) throws RegistryException {
        return editCommentPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(editCommentPattern));
    }

    public boolean handleRemoveComment(RequestContext requestContext) throws RegistryException {
        return removeCommentPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(removeCommentPattern));
    }

    public boolean handleGetComments(RequestContext requestContext) throws RegistryException {
        return getCommentsPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(getCommentsPattern));
    }

    public boolean handleRateResource(RequestContext requestContext) throws RegistryException {
        return rateResourcePattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(rateResourcePattern));
    }

    public boolean handleGetAverageRating(RequestContext requestContext) throws RegistryException {
        return getAverageRatingPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath()
                        .matches(getAverageRatingPattern));
    }

    public boolean handleGetRating(RequestContext requestContext) throws RegistryException {
        return getRatingPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(getRatingPattern));
    }

    public boolean handleCreateVersion(RequestContext requestContext) throws RegistryException {
        return createVersionPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(createVersionPattern));
    }

    public boolean handleGetVersions(RequestContext requestContext) throws RegistryException {
        return getVersionsPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(getVersionsPattern));
    }

    public boolean handleRestoreVersion(RequestContext requestContext) throws RegistryException {
        return restoreVersionPattern != null && (invert !=
                requestContext.getVersionPath().matches(restoreVersionPattern));
    }

    public boolean handleDump(RequestContext requestContext) throws RegistryException {
        return dumpPattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(dumpPattern));
    }

    public boolean handleRestore(RequestContext requestContext) throws RegistryException {
        return restorePattern != null && (invert !=
                requestContext.getResourcePath().getCompletePath().matches(restorePattern));
    }

    /**
     * Method to set get Pattern
     *
     * @param getPattern the get Pattern
     */
    public void setGetPattern(String getPattern) {
        this.getPattern = getPattern;
    }

    /**
     * Method to set get Pattern
     *
     * @param executeQuery the get Pattern
     */
    public void setExecuteQueryPattern(String executeQuery) {
        this.executeQueryPattern = executeQuery;
    }

    /**
     * Method to set put Pattern
     *
     * @param putPattern the put Pattern
     */
    public void setPutPattern(String putPattern) {
        this.putPattern = putPattern;
    }

    /**
     * Method to set import Pattern
     *
     * @param importPattern the import Pattern
     */
    public void setImportPattern(String importPattern) {
        this.importPattern = importPattern;
    }

    /**
     * Method to set delete Pattern
     *
     * @param deletePattern the delete Pattern
     */
    public void setDeletePattern(String deletePattern) {
        this.deletePattern = deletePattern;
    }

    /**
     * Method to set putChild Pattern
     *
     * @param putChildPattern the putChild Pattern
     */
    public void setPutChildPattern(String putChildPattern) {
        this.putChildPattern = putChildPattern;
    }

    /**
     * Method to set importChild Pattern
     *
     * @param importChildPattern the importChild Pattern
     */
    public void setImportChildPattern(String importChildPattern) {
        this.importChildPattern = importChildPattern;
    }

    /**
     * Method to set invokeAspect Pattern
     *
     * @param invokeAspectPattern the invokeAspect Pattern
     */
    public void setInvokeAspectPattern(String invokeAspectPattern) {
        this.invokeAspectPattern = invokeAspectPattern;
    }

    /**
     * Method to set move Pattern
     *
     * @param movePattern the move Pattern
     */
    public void setMovePattern(String movePattern) {
        this.movePattern = movePattern;
    }

    /**
     * Method to set copy Pattern
     *
     * @param copyPattern the copy Pattern
     */
    public void setCopyPattern(String copyPattern) {
        this.copyPattern = copyPattern;
    }

    /**
     * Method to set rename Pattern
     *
     * @param renamePattern the rename Pattern
     */
    public void setRenamePattern(String renamePattern) {
        this.renamePattern = renamePattern;
    }

    /**
     * Method to set createLink Pattern
     *
     * @param createLinkPattern the createLink Pattern
     */
    public void setCreateLinkPattern(String createLinkPattern) {
        this.createLinkPattern = createLinkPattern;
    }

    /**
     * Method to set removeLink Pattern
     *
     * @param removeLinkPattern the removeLink Pattern
     */
    public void setRemoveLinkPattern(String removeLinkPattern) {
        this.removeLinkPattern = removeLinkPattern;
    }

    /**
     * Method to set resourceExists Pattern
     *
     * @param resourceExistsPattern the resourceExists Pattern
     */
    public void setResourceExistsPattern(String resourceExistsPattern) {
        this.resourceExistsPattern = resourceExistsPattern;
    }

    /**
     * Method to set getRegistryContext Pattern
     *
     * @param getRegistryContextPattern the getRegistryContext Pattern
     */
    public void setGetRegistryContextPattern(String getRegistryContextPattern) {
        this.getRegistryContextPattern = getRegistryContextPattern;
    }

    /**
     * Method to set addAssociation Pattern
     *
     * @param addAssociationPattern the addAssociation Pattern
     */
    public void setAddAssociationPattern(String addAssociationPattern) {
        this.addAssociationPattern = addAssociationPattern;
    }

    /**
     * Method to set removeAssociation Pattern
     *
     * @param removeAssociationPattern the removeAssociation Pattern
     */
    public void setRemoveAssociationPattern(String removeAssociationPattern) {
        this.removeAssociationPattern = removeAssociationPattern;
    }

    /**
     * Method to set getAllAssociations Pattern
     *
     * @param getAllAssociationsPattern the getAllAssociations Pattern
     */
    public void setGetAllAssociationsPattern(String getAllAssociationsPattern) {
        this.getAllAssociationsPattern = getAllAssociationsPattern;
    }

    /**
     * Method to set getAssociations Pattern
     *
     * @param getAssociationsPattern the getAssociations Pattern
     */
    public void setGetAssociationsPattern(String getAssociationsPattern) {
        this.getAssociationsPattern = getAssociationsPattern;
    }

    /**
     * Method to set applyTag Pattern
     *
     * @param applyTagPattern the applyTag Pattern
     */
    public void setApplyTagPattern(String applyTagPattern) {
        this.applyTagPattern = applyTagPattern;
    }

    /**
     * Method to set getTags Pattern
     *
     * @param getTagsPattern the getTags Pattern
     */
    public void setGetTagsPattern(String getTagsPattern) {
        this.getTagsPattern = getTagsPattern;
    }

    /**
     * Method to set removeTag Pattern
     *
     * @param removeTagPattern the removeTag Pattern
     */
    public void setRemoveTagPattern(String removeTagPattern) {
        this.removeTagPattern = removeTagPattern;
    }

    /**
     * Method to set addComment Pattern
     *
     * @param addCommentPattern the addComment Pattern
     */
    public void setAddCommentPattern(String addCommentPattern) {
        this.addCommentPattern = addCommentPattern;
    }

    /**
     * Method to set editComment Pattern
     *
     * @param editCommentPattern the editComment Pattern
     */
    public void setEditCommentPattern(String editCommentPattern) {
        this.editCommentPattern = editCommentPattern;
    }

    /**
     * Method to set removeComment Pattern
     *
     * @param removeCommentPattern the removeComment Pattern
     */
    public void setRemoveCommentPattern(String removeCommentPattern) {
        this.removeCommentPattern = removeCommentPattern;
    }

    /**
     * Method to set getComments Pattern
     *
     * @param getCommentsPattern the getComments Pattern
     */
    public void setGetCommentsPattern(String getCommentsPattern) {
        this.getCommentsPattern = getCommentsPattern;
    }

    /**
     * Method to set rateResource Pattern
     *
     * @param rateResourcePattern the rateResource Pattern
     */
    public void setRateResourcePattern(String rateResourcePattern) {
        this.rateResourcePattern = rateResourcePattern;
    }

    /**
     * Method to set getAverageRating Pattern
     *
     * @param getAverageRatingPattern the getAverageRating Pattern
     */
    public void setGetAverageRatingPattern(String getAverageRatingPattern) {
        this.getAverageRatingPattern = getAverageRatingPattern;
    }

    /**
     * Method to set getRating Pattern
     *
     * @param getRatingPattern the getRating Pattern
     */
    public void setGetRatingPattern(String getRatingPattern) {
        this.getRatingPattern = getRatingPattern;
    }

    /**
     * Method to set createVersion Pattern
     *
     * @param createVersionPattern the createVersion Pattern
     */
    public void setCreateVersionPattern(String createVersionPattern) {
        this.createVersionPattern = createVersionPattern;
    }

    /**
     * Method to set getVersions Pattern
     *
     * @param getVersionsPattern the getVersions Pattern
     */
    public void setGetVersionsPattern(String getVersionsPattern) {
        this.getVersionsPattern = getVersionsPattern;
    }

    /**
     * Method to set restoreVersion Pattern
     *
     * @param restoreVersionPattern the restoreVersion Pattern
     */
    public void setRestoreVersionPattern(String restoreVersionPattern) {
        this.restoreVersionPattern = restoreVersionPattern;
    }

    /**
     * Method to set dump Pattern
     *
     * @param dumpPattern the dump Pattern
     */
    public void setDumpPattern(String dumpPattern) {
        this.dumpPattern = dumpPattern;
    }

    /**
     * Method to set restore Pattern
     *
     * @param restorePattern the restore Pattern
     */
    public void setRestorePattern(String restorePattern) {
        this.restorePattern = restorePattern;
    }

    /**
     * Method to set the given pattern for all registry operations.
     *
     * @param pattern the pattern to set.
     */
    public void setPattern(String pattern) {
        setResourceExistsPattern(pattern);
        setGetRegistryContextPattern(pattern);
        setGetPattern(pattern);
        setExecuteQueryPattern(pattern);
        setPutPattern(pattern);
        setDeletePattern(pattern);
        setRenamePattern(pattern);
        setMovePattern(pattern);
        setCopyPattern(pattern);
        setGetAverageRatingPattern(pattern);
        setGetRatingPattern(pattern);
        setRateResourcePattern(pattern);
        setGetCommentsPattern(pattern);
        setEditCommentPattern(pattern);
        setAddCommentPattern(pattern);
        setRemoveCommentPattern(pattern);
        setGetTagsPattern(pattern);
        setRemoveTagPattern(pattern);
        setApplyTagPattern(pattern);
        setGetAllAssociationsPattern(pattern);
        setGetAssociationsPattern(pattern);
        setAddAssociationPattern(pattern);
        setDumpPattern(pattern);
        setRestorePattern(pattern);
        setCreateVersionPattern(pattern);
        setGetVersionsPattern(pattern);
        setRestoreVersionPattern(pattern);
        setRemoveAssociationPattern(pattern);
        setImportPattern(pattern);
        setCreateLinkPattern(pattern);
        setRemoveLinkPattern(pattern);
        setInvokeAspectPattern(pattern);
        setImportChildPattern(pattern);
        setPutChildPattern(pattern);
    }
}
