/**
 * Copyright (C) 2015 Per Ivar Gjerløw
 * All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 */
package org.karivar.utils;

import org.karivar.utils.domain.IssueKeyNotFoundException;
import org.karivar.utils.domain.JiraIssue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class GitHook {
    private final Logger logger = LoggerFactory.getLogger(GitHook.class);
    private static GitHook githook;
    private ResourceBundle messages;
    private static CommitMessageManipulator manipulator;
    private JiraConnector jiraConnector;
    private JiraIssue populatedIssue;

    public static void main(String[] args) {
        githook = new GitHook();
        manipulator = new CommitMessageManipulator();
        githook.init(args);
    }

    public void init(String[] args) {

        loadI18nMessages(GitConfig.getLanguageSettings());
        printInitalText();

        if (args != null && args.length > 0) {
            manipulator.loadCommitMessage(args[0]);

            // Load JIRA project list
           Optional<String> jiraProjectKeys =  GitConfig.getJiraProjects();

            // Load JIRA issue types and their accepted statuses.

            // Get options for
            //   1: override communication with JIRA altogether
            //   2: override (e.g force) commits
            boolean isJiraCommunicationOverridden = manipulator.isCommunicationOverridden();
            boolean isCommitOverridden = manipulator.isCommitOverridden();

            if (!isJiraCommunicationOverridden && !isCommitOverridden) {
                logger.debug("Preparing to communicate with Jira");

                JiraConnector jiraConnector = new JiraConnector();
                jiraConnector.connectToJira(GitConfig.getJiraUsername(),
                        GitConfig.getJiraEncodedPassword(), GitConfig.getJiraAddress());

                try {
                    Optional<String> issueKey = manipulator.getJiraIssueKeyFromCommitMessage(
                            getJiraIssueKey(jiraProjectKeys));
                    populatedIssue = jiraConnector.getJiraPopulatedIssue(issueKey);
                } catch (IssueKeyNotFoundException e) {
                    logger.error(e.getLocalizedMessage());
                }


            } else {
                logger.debug("Communication with Jira is overridden or commit is overridden");
            }

            // Contact JIRA, fetch JIRA issue and check state and return populated issue

            // If state is OK, manipulate commit message.

            // Update the commit message file and allow commit


        } else {
            logger.error(messages.getString("error.githook.nocommitfile"));
        }
    }

    private void loadI18nMessages(Optional<String> languageSettings) {
        if (languageSettings.isPresent()) {
            messages = ResourceBundle.getBundle("messages", Locale.forLanguageTag(languageSettings.get()));
        } else messages = ResourceBundle.getBundle("messages");
    }

    private void printInitalText() {
        logger.info(messages.getString("startup.information") +  " 0.0.1");
    }

    private String getJiraIssueKey(Optional<String> jiraProjectPattern) {
        String issueKey = null;

        if (jiraProjectPattern.isPresent()) {

            issueKey = null;

//            try {
                Optional<String> possibleIssueKey = manipulator.getJiraIssueKeyFromCommitMessage(
                        jiraProjectPattern.get());
                if (possibleIssueKey.isPresent()) {
                    issueKey = possibleIssueKey.get();
                }
//            } catch (NoSuchElementException e) {
//
//            }

//            try {
//                issueKey = manipulator.getJiraIssueKeyFromCommitMessage(jiraProjectPattern.get();
//                logger.debug("found key {}", issueKey);
//            } catch (IssueKeyNotFoundException e) {
//                e.printStackTrace();
//            } catch (java.util.NoSuchElementException e) {
//                e.printStackTrace();
//            }
        } else {
            logger.debug("There are no project keys registered in git config");
        }

        return issueKey;
    }


}
