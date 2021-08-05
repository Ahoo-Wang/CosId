/*
 * Copyright [2021-2021] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.ahoo.cosid.rest.repository;

import me.ahoo.cosid.rest.entity.FriendlyIdEntity;
import me.ahoo.cosid.rest.entity.LongIdEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * @author ahoo wang
 */
@Mapper
public interface EntityRepository {

    @Insert("insert into t_table (id) value (#{id});")
    void insert(LongIdEntity order);

    @Insert({
            "<script>",
            "insert into t_friendly_table (id)",
            "VALUES" +
                    "<foreach item='item' collection='list' open='' separator=',' close=''>" +
                    "(#{item.id})" +
                    "</foreach>",
            "</script>"})
    void insertList(List<FriendlyIdEntity> list);
}
