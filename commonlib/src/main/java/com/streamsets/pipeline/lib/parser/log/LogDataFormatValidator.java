/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.lib.parser.log;

import com.streamsets.pipeline.api.Source;
import com.streamsets.pipeline.api.Stage;
import com.streamsets.pipeline.config.LogMode;
import com.streamsets.pipeline.config.OnParseError;
import com.streamsets.pipeline.lib.parser.CharDataParserFactory;
import com.streamsets.pipeline.lib.parser.DataParserException;
import com.streamsets.pipeline.lib.parser.shaded.org.aicer.grok.dictionary.GrokDictionary;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class LogDataFormatValidator {

  private final LogMode logMode;
  private final int logMaxObjectLen;
  private final boolean logRetainOriginalLine;
  private final String customLogFormat;
  private final String regex;
  private final String grokPatternDefinition;
  private final String grokPattern;
  private final Map<String, Integer> fieldPathsToGroupName;
  private final boolean enableLog4jCustomLogFormat;
  private final String log4jCustomLogFormat;
  private final int maxStackTraceLines;
  private final OnParseError onParseError;
  private final String groupName;

  public LogDataFormatValidator(LogMode logMode, int logMaxObjectLen, boolean logRetainOriginalLine,
                                String customLogFormat, String regex, String grokPatternDefinition,
                                String grokPattern, boolean enableLog4jCustomLogFormat, String log4jCustomLogFormat,
                                OnParseError onParseError, int maxStackTraceLines, String groupName,
                                Map<String, Integer> fieldPathsToGroupName) {
    this.logMode = logMode;
    this.logMaxObjectLen = logMaxObjectLen;
    this.logRetainOriginalLine = logRetainOriginalLine;
    this.customLogFormat = customLogFormat;
    this.regex = regex;
    this.grokPatternDefinition = grokPatternDefinition;
    this.grokPattern = grokPattern;
    this.enableLog4jCustomLogFormat = enableLog4jCustomLogFormat;
    this.log4jCustomLogFormat = log4jCustomLogFormat;
    this.maxStackTraceLines = maxStackTraceLines;
    this.groupName = groupName;
    this.fieldPathsToGroupName = fieldPathsToGroupName;
    this.onParseError = onParseError;
  }

  public void populateBuilder(CharDataParserFactory.Builder builder) {
    builder.setMaxDataLen(logMaxObjectLen)
      .setConfig(LogCharDataParserFactory.RETAIN_ORIGINAL_TEXT_KEY, logRetainOriginalLine)
      .setConfig(LogCharDataParserFactory.APACHE_CUSTOMLOG_FORMAT_KEY, customLogFormat)
      .setConfig(LogCharDataParserFactory.REGEX_KEY, regex)
      .setConfig(LogCharDataParserFactory.REGEX_FIELD_PATH_TO_GROUP_KEY, fieldPathsToGroupName)
      .setConfig(LogCharDataParserFactory.GROK_PATTERN_DEFINITION_KEY, grokPatternDefinition)
      .setConfig(LogCharDataParserFactory.GROK_PATTERN_KEY, grokPattern)
      .setConfig(LogCharDataParserFactory.LOG4J_FORMAT_KEY, log4jCustomLogFormat)
      .setConfig(LogCharDataParserFactory.ON_PARSE_ERROR_KEY, onParseError)
      .setConfig(LogCharDataParserFactory.LOG4J_TRIM_STACK_TRACES_TO_LENGTH_KEY, maxStackTraceLines)
      .setMode(logMode);
  }

  public void validateLogFormatConfig(List<Stage.ConfigIssue> issues, Source.Context context) {
    if (logMaxObjectLen < 1) {
      issues.add(context.createConfigIssue(groupName, "logMaxObjectLen", Errors.LOG_PARSER_04, logMaxObjectLen));
    }
    if(maxStackTraceLines < 0) {
      issues.add(context.createConfigIssue(groupName, "maxStackTraceLines", Errors.LOG_PARSER_10, maxStackTraceLines));
    }
    if(logMode == LogMode.APACHE_CUSTOM_LOG_FORMAT) {
      validateApacheCustomLogFormat(issues, context);
    } else if(logMode == LogMode.REGEX) {
      validateRegExFormat(issues, context);
    } else if(logMode == LogMode.GROK) {
      validateGrokPattern(issues, context);
    } else if (logMode == LogMode.LOG4J) {
      validateLog4jCustomLogFormat(issues, context);
    }
  }

  private void validateApacheCustomLogFormat(List<Stage.ConfigIssue> issues, Source.Context context) {
    if(customLogFormat == null || customLogFormat.isEmpty()) {
      issues.add(context.createConfigIssue(groupName, "customLogFormat", Errors.LOG_PARSER_05, customLogFormat));
      return;
    }
    try {
      ApacheCustomLogHelper.translateApacheLayoutToGrok(customLogFormat);
    } catch (DataParserException e) {
      issues.add(context.createConfigIssue(groupName, "customLogFormat", Errors.LOG_PARSER_06, customLogFormat,
        e.getMessage(), e));
    }
  }

  private void validateLog4jCustomLogFormat(List<Stage.ConfigIssue> issues, Source.Context context) {
    if(enableLog4jCustomLogFormat) {
      if (log4jCustomLogFormat == null || log4jCustomLogFormat.isEmpty()) {
        issues.add(context.createConfigIssue(groupName, "log4jCustomLogFormat", Errors.LOG_PARSER_05,
          log4jCustomLogFormat));
        return;
      }
      try {
        Log4jHelper.translateLog4jLayoutToGrok(log4jCustomLogFormat);
      } catch (DataParserException e) {
        issues.add(context.createConfigIssue(groupName, "log4jCustomLogFormat", Errors.LOG_PARSER_06,
          log4jCustomLogFormat, e.getMessage(), e));
      }
    }
  }

  private void validateRegExFormat(List<Stage.ConfigIssue> issues, Source.Context context) {
    try {
      Pattern compile = Pattern.compile(regex);
      Matcher matcher = compile.matcher(" ");
      int groupCount = matcher.groupCount();

      for(int group : fieldPathsToGroupName.values()) {
        if(group > groupCount) {
          issues.add(context.createConfigIssue(groupName, "fieldPathsToGroupName", Errors.LOG_PARSER_08,
            regex, groupCount, group));
        }
      }
    } catch (PatternSyntaxException e) {
      issues.add(context.createConfigIssue(groupName, "regex", Errors.LOG_PARSER_07,
        regex, e.getMessage(), e));
    }
  }

  private void validateGrokPattern(List<Stage.ConfigIssue> issues, Source.Context context) {
    try {
      GrokDictionary grokDictionary = new GrokDictionary();
      grokDictionary.addDictionary(getClass().getClassLoader().getResourceAsStream(Constants.GROK_PATTERNS_FILE_NAME));
      grokDictionary.addDictionary(getClass().getClassLoader().getResourceAsStream(
        Constants.GROK_JAVA_LOG_PATTERNS_FILE_NAME));
      if(grokPatternDefinition != null || !grokPatternDefinition.isEmpty()) {
        grokDictionary.addDictionary(new StringReader(grokPatternDefinition));
      }
      grokDictionary.bind();
      grokDictionary.compileExpression(grokPattern);
    } catch (PatternSyntaxException e) {
      issues.add(context.createConfigIssue(groupName, "regex", Errors.LOG_PARSER_09,
        regex, e.getMessage(), e));
    }
  }

}