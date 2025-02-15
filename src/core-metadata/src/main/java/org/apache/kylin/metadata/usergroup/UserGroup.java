/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kylin.metadata.usergroup;

import org.apache.kylin.common.persistence.RootPersistentEntity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

import static org.apache.kylin.common.persistence.ResourceStore.USER_GROUP_ROOT;

@SuppressWarnings("serial")
@Getter
@Setter
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE, setterVisibility = JsonAutoDetect.Visibility.NONE)
public class UserGroup extends RootPersistentEntity {
    @JsonProperty("group_name")
    private String groupName;

    public UserGroup() {
    }

    public UserGroup(String groupName) {
        this.groupName = groupName;
    }

    @Override
    public String resourceName() {
        return groupName;
    }

    @Override
    public String getResourcePath() {
        return USER_GROUP_ROOT + "/" + resourceName();
    }
}
