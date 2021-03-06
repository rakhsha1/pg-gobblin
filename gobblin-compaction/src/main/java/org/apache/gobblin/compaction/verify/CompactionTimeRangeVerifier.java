/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.gobblin.compaction.verify;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.gobblin.compaction.dataset.TimeBasedSubDirDatasetsFinder;
import org.apache.gobblin.compaction.mapreduce.MRCompactor;
import org.apache.gobblin.compaction.parser.CompactionPathParser;
import org.apache.gobblin.configuration.State;
import org.apache.gobblin.dataset.FileSystemDataset;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

/**
 * A simple class which verify current dataset belongs to a specific time range. Will skip to do
 * compaction if dataset is not in a correct time range.
 */

@Slf4j
@AllArgsConstructor
public class CompactionTimeRangeVerifier implements CompactionVerifier<FileSystemDataset> {
  public final static String COMPACTION_VERIFIER_TIME_RANGE = COMPACTION_VERIFIER_PREFIX + "time-range";

  protected State state;

  public Result verify (FileSystemDataset dataset) {
    final DateTime earliest;
    final DateTime latest;
    try {
      CompactionPathParser.CompactionParserResult result = new CompactionPathParser(state).parse(dataset);
      DateTime folderTime = result.getTime();
      DateTimeZone timeZone = DateTimeZone.forID(this.state.getProp(MRCompactor.COMPACTION_TIMEZONE, MRCompactor.DEFAULT_COMPACTION_TIMEZONE));
      DateTime current = new DateTime(timeZone);
      PeriodFormatter formatter = new PeriodFormatterBuilder().appendMonths().appendSuffix("m").appendDays().appendSuffix("d").appendHours()
              .appendSuffix("h").toFormatter();

      // get earliest time
      String maxTimeAgoStr = this.state.getProp(TimeBasedSubDirDatasetsFinder.COMPACTION_TIMEBASED_MAX_TIME_AGO, TimeBasedSubDirDatasetsFinder.DEFAULT_COMPACTION_TIMEBASED_MAX_TIME_AGO);
      Period maxTimeAgo = formatter.parsePeriod(maxTimeAgoStr);
      earliest = current.minus(maxTimeAgo);

      // get latest time
      String minTimeAgoStr = this.state.getProp(TimeBasedSubDirDatasetsFinder.COMPACTION_TIMEBASED_MIN_TIME_AGO, TimeBasedSubDirDatasetsFinder.DEFAULT_COMPACTION_TIMEBASED_MIN_TIME_AGO);
      Period minTimeAgo = formatter.parsePeriod(minTimeAgoStr);
      latest = current.minus(minTimeAgo);

      if (earliest.isBefore(folderTime) && latest.isAfter(folderTime)) {
        log.debug("{} falls in the user defined time range", dataset.datasetRoot());
        return new Result(true, "");
      }
    } catch (Exception e) {
      log.error("{} cannot be verified because of {}", dataset.datasetRoot(), ExceptionUtils.getFullStackTrace(e));
      return new Result(false, e.toString());
    }
    return new Result(false, dataset.datasetRoot() + " is not in between " + earliest + " and " + latest);
  }

  public String getName() {
    return COMPACTION_VERIFIER_TIME_RANGE;
  }

  public boolean isRetriable () {
    return false;
  }
}
