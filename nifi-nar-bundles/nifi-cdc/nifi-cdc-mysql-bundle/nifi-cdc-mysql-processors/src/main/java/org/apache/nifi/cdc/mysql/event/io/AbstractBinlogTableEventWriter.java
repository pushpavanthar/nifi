/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.cdc.mysql.event.io;

import org.apache.nifi.cdc.event.ColumnDefinition;
import org.apache.nifi.cdc.mysql.event.UpdateRowsEventInfo;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.cdc.mysql.event.BinlogTableEventInfo;

import java.io.IOException;
import java.io.Serializable;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * An abstract base class for writing MYSQL table-related binlog events into flow file(s), e.g.
 */
public abstract class AbstractBinlogTableEventWriter<T extends BinlogTableEventInfo> extends AbstractBinlogEventWriter<T> {

    protected void writeJson(T event) throws IOException {
        super.writeJson(event);
        if (event.getDatabaseName() != null) {
            jsonGenerator.writeStringField("database", event.getDatabaseName());
        } else {
            jsonGenerator.writeNullField("database");
        }
        if (event.getTableName() != null) {
            jsonGenerator.writeStringField("table_name", event.getTableName());
        } else {
            jsonGenerator.writeNullField("table_name");
        }
        if (event.getTableId() != null) {
            jsonGenerator.writeNumberField("table_id", event.getTableId());
        } else {
            jsonGenerator.writeNullField("table_id");
        }
    }

    // Default implementation for table-related binlog events
    @Override
    public long writeEvent(ProcessSession session, String transitUri, T eventInfo, long currentSequenceId, Relationship relationship) {
        FlowFile flowFile = session.create();
        flowFile = session.write(flowFile, (outputStream) -> {
            super.startJson(outputStream, eventInfo);
            writeJson(eventInfo);
            // Nothing in the body
            super.endJson();
        });
        flowFile = session.putAllAttributes(flowFile, getCommonAttributes(currentSequenceId, eventInfo));
        session.transfer(flowFile, relationship);
        session.getProvenanceReporter().receive(flowFile, transitUri);
        return currentSequenceId + 1;
    }


		protected void writeMetaData(T event, Serializable[] row, BitSet includedColumns) throws IOException {
				int i = includedColumns.nextSetBit(0);
				List<ColumnDefinition> pkList = event.getPrimaryKeyColList();
				Map<String, Serializable> columnValueMap = new HashMap<>();
				while (i != -1) {
						ColumnDefinition columnDefinition = event.getColumnByIndex(i);
						columnValueMap.putIfAbsent(columnDefinition.getName(), row[i]);
						i = includedColumns.nextSetBit(i + 1);
				}
				String primaryKey = pkList.stream().map(col -> {
						String pkValue = "";
						if(columnValueMap.containsKey(col.getName())){
								Serializable colValue = columnValueMap.get(col.getName());
								if (colValue instanceof byte[]) {
										pkValue = new String((byte[]) colValue);
								} else {
										pkValue = colValue.toString();
								}
						}
						return pkValue;}).collect(Collectors.joining("_"));
				jsonGenerator.writeStringField("primary_key", primaryKey);
		}
}
