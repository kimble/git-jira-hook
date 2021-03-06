/*
 * Copyright (C) 2015 Per Ivar Gjerløw
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */
package org.karivar.utils;

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.karivar.utils.domain.BasicJiraIssue;
import org.karivar.utils.domain.JiraIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import com.google.common.io.Files;

import java.util.*;


/**
 * This class is responsible for handling of the commit message. This includes reading, saving and extracting
 * information (e.g the given Jira issue key and options)
 */
class CommitMessageManipulator {
    private final Logger logger = LoggerFactory.getLogger(CommitMessageManipulator.class);

    private static final String JIRA_COMMUNICATION_OVERRIDDEN = "-O";
    private static final String JIRA_ASSIGNEE_OVERRIDDEN = "-A";
    private static final String JIRA_COMMIT_OVERRIDDEN = "NONE";

    private final ResourceBundle messages;
    private String commitMessageFilename;
    private List<String> commitFileContents = null;
    private boolean jiraIssueKeyFound;

    public CommitMessageManipulator(ResourceBundle bundle) {
        messages = bundle;
    }

    void loadCommitMessage(String filename) {

        // Load the commit message file
        if (!Strings.isNullOrEmpty(filename)) {
            try {
                File file = new File(filename);
                commitFileContents = Files.readLines(file, Charsets.UTF_8);
                logger.debug("The file contents are: \n\t {}", commitFileContents);
                commitMessageFilename = filename;

            } catch (FileNotFoundException e) {
                logger.error(messages.getString("error.loadfile.filenotfound") + filename);
            } catch (IOException e) {
                logger.error(messages.getString("loadfile.commit.io"));
            }
        } else {
            logger.error(messages.getString("error.loadfile.filenotfound") + filename);
        }
    }

    boolean isCommunicationOverridden() {
        boolean isOverridden = false;
        if (commitFileContents != null && commitFileContents.size() > 0) {
            String[] wordList = commitFileContents.get(0).split("\\s+");
            String last = wordList[wordList.length - 1];

            if (last.equalsIgnoreCase(JIRA_COMMUNICATION_OVERRIDDEN)) {
                isOverridden = true;
            }
        }
        return isOverridden;
    }

    boolean isCommitOverridden() {
        boolean isCommitOverridden = false;
        if (commitFileContents != null && commitFileContents.size() > 0) {
            String[] wordList = commitFileContents.get(0).split("\\s+");

            if (wordList.length > 0) {
                String first = wordList[0];

                if (first.equalsIgnoreCase(JIRA_COMMIT_OVERRIDDEN)) {
                    isCommitOverridden = true;
                }
            }
        }
        return isCommitOverridden;
    }

    boolean isAssigneeOverridden() {
        boolean isAssigneeOverridden = false;
        if (commitFileContents != null && commitFileContents.size() > 0) {
            String[] wordList = commitFileContents.get(0).split("\\s+");
            String last = wordList[wordList.length - 1];

            if (last.equalsIgnoreCase(JIRA_ASSIGNEE_OVERRIDDEN)) {
                isAssigneeOverridden = true;
            }
        }
        return isAssigneeOverridden;
    }


    Optional<String> getJiraIssueKeyFromCommitMessage(String jiraIssuePattern) {
        Optional<String> jiraIssueKey = Optional.empty();
        if (commitFileContents != null && commitFileContents.size() > 0) {
            String firstLineOfCommitMessage = commitFileContents.get(0);
            logger.debug("Starting getJiraIssueKeyFromCommitMessage({}, {})", firstLineOfCommitMessage, jiraIssuePattern);

            if (!Strings.isNullOrEmpty(firstLineOfCommitMessage)) {

                if (!Strings.isNullOrEmpty(jiraIssuePattern)) {
                    Splitter jiraPatternSplitter = Splitter.on(" ");
                    List<String> commitLineWords = jiraPatternSplitter.splitToList(firstLineOfCommitMessage);
                    List<String> jiraIssuePatterns = jiraPatternSplitter.splitToList(jiraIssuePattern);

                    if (!commitLineWords.isEmpty()) {
                        for (String pattern : jiraIssuePatterns) {
                            for (String word : commitLineWords) {
                                if (word.toUpperCase().startsWith(pattern.toUpperCase())) {
                                    jiraIssueKey = Optional.of(word.toUpperCase());
                                    jiraIssueKeyFound = true;
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    logger.error(messages.getString("githook.jiraissue.pattern.notfound"));
                }
            } else {
                logger.error(messages.getString("githook.jiraissue.empty"));
            }
        }

        return jiraIssueKey;
    }

    /**
     * Removes any options from the original commit message (first line only)
     * @return the first line without any options
     */
    List<String> getStrippedCommitMessage() {
        ArrayList<String> strippedCommitMessage = Lists.newArrayList();
        if (commitFileContents != null && commitFileContents.size() > 0) {
            strippedCommitMessage = (ArrayList<String>) commitFileContents;
            strippedCommitMessage.set(0, getStrippedFirstCommitLine(strippedCommitMessage.get(0)));
        }

        return strippedCommitMessage;
    }

    /**
     * Manipulates the commit message and adds information according to the convention below. In addition
     * updates the commit message with the manipulated message.<br>
     * &lt;ORIGINAL_JIRAISSUE_KEY&gt; &lt;COMMIT MESSAGE&gt;<br>
     * &lt;empty line&gt;<br>
     * summary: &lt;JIRAISSUE_SUMMARY&gt;<br>
     * sub-task of: &lt;PARENT_JIRAISSUE_KEY&gt; &lt;PARENT__JIRAISSUE_SUMMARY&gt; (optional)<br>
     * related to: &lt;RELATED_JIRAISSUE_KEY&gt; &lt;RELATED_JIRAISSUE_SUMMARY&gt; (optional)<br>
     * &lt;empty line&gt; (optional)<br>
     * &lt;additional information&gt; (optional)<br>
     * &lt;hook version information&gt;<br>
     * @param populatedIssue the populated JIRA issue
     * @param hookInformation string containing information about the hook
     * @param filename The name of the file to be written to. Not null when testing only!
     * @param communicationOverridden true if the communication with JIRA is overridden
     * @param assigneeOverridden true if assignee is overrridden
     */
    void manipulateCommitMessage(JiraIssue populatedIssue, String hookInformation, String filename,
                                        boolean communicationOverridden, boolean assigneeOverridden) {
        List<String> manipulatedMessage = getStrippedCommitMessage();
        if (manipulatedMessage != null && manipulatedMessage.size() > 0 ) {
            manipulatedMessage = addTraceabilityInformationToMessage(manipulatedMessage, populatedIssue,
                    hookInformation, communicationOverridden, assigneeOverridden);
            logger.debug("The manipulated message is {}", manipulatedMessage);
            writeCommitMessage(manipulatedMessage, filename);
        } else {
            logger.error("The commit message is empty");
        }
    }

    /**
     *
     * @param populatedIssue the populated JIRA issue
     * @param jiraCommunicationOverridden true if the communication with JIRA is overridden
     * @param filename The name of the file to be written to. Not null when testing only!
     * @param hookVersion The version number of the hook
     * @return true if everything went fine. Otherwise false is returned.
     */
    boolean checkStateAndManipulateCommitMessage(JiraIssue populatedIssue, boolean jiraCommunicationOverridden,
                                                 String filename, String hookVersion) {
        // check status against allowed statues
        boolean statusOK = checkAllowedStatus(populatedIssue);

        boolean assigneeOK;
        boolean assigneeOverridden = isAssigneeOverridden();
        if (!assigneeOverridden) {
            // check assignee
            assigneeOK = checkAssignee(populatedIssue);
        } else if (isCommitOverridden()) {
            // the commit is overridden. Thus assignee is not found, but OK anyway
            assigneeOK = true;
        } else {
            assigneeOK = assigneeOverridden;
        }

        if (statusOK && assigneeOK) {
            // Status is OK. Start manipulating commit message and accept commits to repo
            manipulateCommitMessage(populatedIssue, getHookInformation(hookVersion), filename,
                    jiraCommunicationOverridden, assigneeOverridden);

        } else {
            // Status is not OK.
            if (!statusOK) {
                if (populatedIssue != null) {
                    logger.info(messages.getString("commitnotallowedstatus")
                            + populatedIssue.getStatus());
                }

                if (!assigneeOK) {
                    if (populatedIssue.getAssignee() != null && populatedIssue.getAssignee().isPresent()) {
                        logger.info(messages.getString("commitnotallowedassignee")
                                + populatedIssue.getAssignee().get().getDisplayName());
                    } else {
                        logger.info(messages.getString("commitnotallowedassigneeunknown"));
                    }
                }
            }
            return false;
        }
        return true;
    }

    String getJiraIssueKeyFromPattern(String jiraProjectPattern) {
        String issueKey = null;

        if (jiraProjectPattern != null) {

            Optional<String> possibleIssueKey = getJiraIssueKeyFromCommitMessage(
                    jiraProjectPattern);

            if (possibleIssueKey.isPresent()) {
                issueKey = possibleIssueKey.get();
            }

        } else {
            logger.debug("There are no project keys registered in git config");
        }

        return issueKey;
    }

    private void writeCommitMessage(List<String> commitFileContents, String filename) {
        File file;
        // Filename is not null for testing
        if (Strings.isNullOrEmpty(filename)) {
            file = new File(commitMessageFilename);
        } else {
            file = new File(filename);
        }
        try {
            Files.asCharSink(file, Charsets.UTF_8).writeLines(commitFileContents);
        } catch (IOException e) {
            logger.error(messages.getString("writefile.commit.io"), e);
        }
    }

    private boolean checkAllowedStatus(JiraIssue populatedIssue) {

        if (isCommunicationOverridden()) {
            return true;
        }

        if (isCommitOverridden()){
            // Have to handle if NONE as first parameter is used
            return true;
        }

        if (populatedIssue != null) {
            PropertyReader propertyReader = new PropertyReader(messages);
            List<String> issueTypeStatuses = propertyReader.getIssueTypesAndStatuses()
                    .get(populatedIssue.getIssueTypeName());

            if (issueTypeStatuses != null && !issueTypeStatuses.isEmpty()) {

                for (String status : issueTypeStatuses) {
                    if (status.equals(populatedIssue.getStatus())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private String getHookInformation(String version) {
        return messages.getString("commit.convention.hookinformation") + version;
    }

    private boolean checkAssignee(JiraIssue populatedIssue) {
        if (populatedIssue != null
                && populatedIssue.getAssignee() != null
                && populatedIssue.getAssignee().isPresent()) {
            String assignedUsername = populatedIssue.getAssignee().get().getName();

            if (!Strings.isNullOrEmpty(GitConfig.getJiraUsername())
                    && assignedUsername.equals(GitConfig.getJiraUsername())) {
                return true;
            }
        } else {
            return true;
        }

        return false;
    }

    private List<String> addTraceabilityInformationToMessage(final List<String> manipulatedMessage,
                                                             JiraIssue populatedIssue, String hookInformation,
                                                             boolean communicationOverridden,
                                                             boolean assigneeOverridden) {
        ArrayList<String> addedTraceabilityMessage = (ArrayList<String>) manipulatedMessage;
        addedTraceabilityMessage.add("");
        String summaryInfo = getSummaryInformation(populatedIssue);

        if (summaryInfo !=null) {
            addedTraceabilityMessage.add(summaryInfo);
        }

        if (populatedIssue != null && populatedIssue.isSubtask()) {
            addedTraceabilityMessage.add(getParentIssueInformation(populatedIssue));
        }

        List<String> relatedIssues = getRelatedIssuesInformation(populatedIssue);
        if (relatedIssues != null) {
            addedTraceabilityMessage.addAll(relatedIssues);
        }

        List<String> additionalInformation = getAdditionalInformation(communicationOverridden, assigneeOverridden);

        if (additionalInformation.size() >= 1) {
            addedTraceabilityMessage.addAll(additionalInformation);
        }

        addedTraceabilityMessage.add(hookInformation);


        return addedTraceabilityMessage;
    }

    private String getSummaryInformation(JiraIssue populatedIssue) {
        if (populatedIssue != null) {
            return messages.getString("commit.convention.summary") + populatedIssue.getSummary();
        }
        return null;
    }

    private String getParentIssueInformation(JiraIssue populatedIssue) {
        if (populatedIssue.getParentIssue() != null && populatedIssue.getParentIssue().isPresent()) {
            BasicJiraIssue parent = populatedIssue.getParentIssue().get();
            return messages.getString("commit.convention.parentissue") + parent.getKey() + " " + parent.getSummary();
        }
        return null;
    }

    private List<String> getRelatedIssuesInformation(JiraIssue populatedIssue) {
        List<String>relatedIssueInformation = null;
        if (populatedIssue != null &&
                populatedIssue.getRelatedIssues() != null &&
                populatedIssue.getRelatedIssues().size() > 0) {
            Iterator<BasicJiraIssue> relatedIssuesIterator = populatedIssue.getRelatedIssues().iterator();
            relatedIssueInformation = new ArrayList<>();
            while (relatedIssuesIterator.hasNext()) {
                BasicJiraIssue relatedIssue = relatedIssuesIterator.next();
                String message = messages.getString("commit.convention.relatedissue") +
                        relatedIssue.getKey() + " " + relatedIssue.getSummary();
                relatedIssueInformation.add(message);
            }
        }
        return relatedIssueInformation;
    }

    private List<String> getAdditionalInformation(boolean jiraCommunicationOverridden, boolean assigneeOverridden) {

        List<String> additionalInfo = new ArrayList<>();

        if (jiraCommunicationOverridden) {
            additionalInfo.add(messages.getString("commit.convention.communicationoverridden"));
        }

        if (assigneeOverridden) {
            additionalInfo.add(messages.getString("commit.convention.assigneeoverridden"));
        }

        return additionalInfo;
    }

    private String getStrippedFirstCommitLine(final String firstLine) {
        List<String> arrayList = Collections.singletonList(firstLine);
        String[] wordList = arrayList.get(0).split("\\s+");

        if (isCommitOverridden() || isCommunicationOverridden() || isAssigneeOverridden() || jiraIssueKeyFound) {
            wordList[0] = wordList[0].toUpperCase();
        }

        // Have to iterate through all words in the 1st line of the commit message
        // and remove the options
        for (int i = 1; i < wordList.length; i++) {
            String word = wordList[i];
            // Assignee
            if (word.equalsIgnoreCase(JIRA_ASSIGNEE_OVERRIDDEN)) {
                wordList = Arrays.copyOf(wordList, wordList.length-1);
            }

            // communication with JIRA
            if (word.equalsIgnoreCase(JIRA_COMMUNICATION_OVERRIDDEN)) {
                wordList = Arrays.copyOf(wordList, wordList.length-1);
            }
        }

        return String.join(" ", wordList);
    }
}



