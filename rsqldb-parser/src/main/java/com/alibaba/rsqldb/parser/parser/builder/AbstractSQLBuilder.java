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
package com.alibaba.rsqldb.parser.parser.builder;

import com.alibaba.rsqldb.parser.parser.SQLBuilderResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.calcite.sql.SqlNode;
import org.apache.rocketmq.streams.common.configurable.IConfigurable;
import org.apache.rocketmq.streams.common.topology.builder.PipelineBuilder;
import org.apache.rocketmq.streams.common.topology.metric.StageGroup;
import org.apache.rocketmq.streams.common.utils.CollectionUtil;
import org.apache.rocketmq.streams.common.utils.PrintUtil;
import org.apache.rocketmq.streams.common.utils.SQLFormatterUtil;

public abstract class AbstractSQLBuilder<T extends AbstractSQLBuilder> implements ISQLBuilder {
    public static SQLFormatterUtil sqlFormatterUtil=new SQLFormatterUtil();
    protected SqlNode sqlNode;//解析节点对应的sqlnode
    protected String sqlType;//sql的类型，是查询还是create
    protected List<T> children = new ArrayList<>();
    protected List<T> parents = new ArrayList<>();
    protected boolean supportOptimization = true;//是否支持优化，可以在不支持解析的地方设置这个参数，builder会忽略对这个节点的优化
    protected String tableName;//表名
    protected String createTable;//这个节点产生的新表
    protected String namespace;//命名空间
    protected PipelineBuilder pipelineBuilder;
    protected Set<String> dependentTables = new HashSet<>();//对于上层table的依赖
    protected String asName;//表的别名，通过as修饰
    protected List<String> scripts = new ArrayList<>();//select，where部分有函数的，则把函数转换成脚本
    protected Map<String, CreateSQLBuilder> tableName2Builders = new HashMap<>();//保存所有create对应的builder， 在insert或维表join时使用
    protected HashSet<String> rootTableNames = new HashSet<>();



    /**
     * 把SQL和stage映射，便于排错
     * @return
     */

    protected String selectSQL;
    protected String fromSQL;
    protected String whereSQL;
    protected String groupSQL;
    protected String havingSQL;
    protected String joinConditionSQL;
    protected List<String> expressionFunctionSQL=new ArrayList<>();

    @Override
    public String getSQLType() {
        return sqlType;
    }

    public void addCreatedTable(String tableName) {
        createTable = tableName;
    }

    @Override
    public boolean supportOptimization() {
        return supportOptimization;
    }

    public void addDependentTable(String tableName) {
        if (tableName != null) {
            dependentTables.add(tableName);
        }

    }

    public String createSQLFromParser(){
        StringBuilder sb=new StringBuilder();
        if(selectSQL!=null){
            sb.append(selectSQL+ PrintUtil.LINE);
        }
        if(fromSQL!=null){
            sb.append(fromSQL+ PrintUtil.LINE);
        }

        if(whereSQL!=null){
            sb.append(whereSQL+ PrintUtil.LINE);
        }

        if(groupSQL!=null){
            sb.append(groupSQL+ PrintUtil.LINE);
        }
        if(havingSQL!=null){
            sb.append(havingSQL+ PrintUtil.LINE);
        }
        return sqlFormatterUtil.format(sb.toString());
    }

    @Override
    public String createSql() {
       return createSQLFromParser();
    }

    public SqlNode getSqlNode() {
        return sqlNode;
    }

    public void setSqlNode(SqlNode sqlNode) {
        this.sqlNode = sqlNode;
    }

    public String getSqlType() {
        return sqlType;
    }

    public void setSqlType(String sqlType) {
        this.sqlType = sqlType;
    }

    public List<T> getChildren() {
        return children;
    }

    public void setChildren(List<T> children) {
        this.children = children;
    }

    public void addChild(T child) {
        this.children.add(child);
        child.getParents().add(this);
    }

    @Override
    public String getCreateTable() {
        return createTable;
    }

    @Override
    public SQLBuilderResult buildSql() {
        if (getPipelineBuilder() == null) {
            PipelineBuilder pipelineBuilder = findPipelineBuilderFromParent(this);
            setPipelineBuilder(pipelineBuilder);
        }
        if (getPipelineBuilder() == null) {
            pipelineBuilder = new PipelineBuilder(getNamespace(), getTableName());
        }

        build();
        return new SQLBuilderResult(pipelineBuilder,this);
    }

    protected PipelineBuilder findPipelineBuilderFromParent(AbstractSQLBuilder<T> sqlBuilder) {
        for (T parent : sqlBuilder.getParents()) {
            if (parent.getPipelineBuilder() != null) {
                return parent.getPipelineBuilder();
            } else {
                PipelineBuilder pipelineBuilder = findPipelineBuilderFromParent(parent);
                if (pipelineBuilder != null) {
                    return pipelineBuilder;
                }
            }
        }
        return null;
    }
    protected PipelineBuilder createPipelineBuilder() {
        PipelineBuilder pipelineBuilder= new PipelineBuilder(this.pipelineBuilder.getPipelineNameSpace(),this.pipelineBuilder.getPipelineName());
        pipelineBuilder.setParentTableName(this.pipelineBuilder.getParentTableName());
        pipelineBuilder.setRootTableName(this.pipelineBuilder.getRootTableName());
        return pipelineBuilder;
    }
    /**
     * 合并一段sql到主sqlbuilder
     * @param sqlBuilderResult
     */
    protected void mergeSQLBuilderResult(SQLBuilderResult sqlBuilderResult){
        List<IConfigurable> configurableList = sqlBuilderResult.getConfigurables();
        if(sqlBuilderResult.isRightJoin()){
            pipelineBuilder.setRightJoin(true);
        }
        if(CollectionUtil.isNotEmpty(configurableList)){
            pipelineBuilder.addConfigurables(configurableList);
        }
        if(CollectionUtil.isNotEmpty(sqlBuilderResult.getStages())){
            pipelineBuilder.getPipeline().getStages().addAll(sqlBuilderResult.getStages());
        }
        if(sqlBuilderResult.getFirstStage()!=null){
            pipelineBuilder.setHorizontalStages(sqlBuilderResult.getFirstStage());
        }
        if(sqlBuilderResult.getLastStage()!=null){
            pipelineBuilder.setCurrentChainStage(sqlBuilderResult.getLastStage());
        }
        if(pipelineBuilder.getCurrentStageGroup()==null&&pipelineBuilder.getParentStageGroup()==null){
            pipelineBuilder.setCurrentStageGroup(sqlBuilderResult.getStageGroup());

        }else {
            if(pipelineBuilder.getParentStageGroup()!=null){
                StageGroup parent=pipelineBuilder.getParentStageGroup();
                sqlBuilderResult.getStageGroup().setParent(parent);
                pipelineBuilder.setCurrentStageGroup(parent);
            }else {
                StageGroup children= pipelineBuilder.getCurrentStageGroup();
                StageGroup parent=sqlBuilderResult.getStageGroup();
                if(children!=null){
                    children.setParent(parent);
                }
                pipelineBuilder.setCurrentStageGroup(parent);
            }


        }

    }
    protected abstract void build();

    public String getFieldName(String fieldName) {
        String name = doAllFieldName(fieldName);
        if (name != null) {
            return name;
        }
        return getFieldName(asName, fieldName);
    }

    /**
     * 获取字段名，对于主流表，会去掉表名前缀，对于维度表，会加上表别名前缀。这里默认是主表逻辑，维度表会覆盖这个方法
     *
     * @param fieldName
     * @return
     */
    protected String getFieldName(String asName, String fieldName) {

        int index = fieldName.indexOf(".");
        if (index == -1) {
            return fieldName;
        }
        String ailasName = fieldName.substring(0, index);
        if (ailasName.equals(asName)) {
            return fieldName.substring(index + 1);
        } else {
            return null;
        }
    }

    public abstract String getFieldName(String fieldName, boolean containsSelf);

    /**
     * 获取字段名，对于主流表，会去掉表名前缀，对于维度表，会加上表别名前缀。这里默认是主表逻辑，维度表会覆盖这个方法
     *
     * @param fieldName
     * @return
     */
    protected String doAllFieldName(String fieldName) {
        if (fieldName.contains("*")) {
            int i = fieldName.indexOf(".");
            if (i != -1) {
                String asName = fieldName.substring(0, i);
                if (asName.equals(getAsName())) {
                    return fieldName;
                } else {
                    return null;
                }
            } else {
                return fieldName;
            }
        }
        return null;
    }

    public String getAsName() {
        return asName;
    }

    public void setAsName(String asName) {
        this.asName = asName;
    }

    public PipelineBuilder getPipelineBuilder() {
        return pipelineBuilder;
    }

    @Override
    public void setPipelineBuilder(PipelineBuilder pipelineBuilder) {
        this.pipelineBuilder = pipelineBuilder;
    }

    public List<T> getParents() {
        return parents;
    }

    public void setParents(List<T> parents) {
        this.parents = parents;
    }

    public boolean isSupportOptimization() {
        return supportOptimization;
    }

    public void setSupportOptimization(boolean supportOptimization) {
        this.supportOptimization = supportOptimization;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public void setCreateTable(String createTable) {
        this.createTable = createTable;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Set<String> getDependentTables() {
        return dependentTables;
    }

    public Map<String, CreateSQLBuilder> getTableName2Builders() {
        return tableName2Builders;
    }

    public void setTableName2Builders(Map<String, CreateSQLBuilder> tableName2Builders) {
        this.tableName2Builders = tableName2Builders;
    }

    public void addScript(String scriptValue) {
        if (scriptValue == null) {
            return;
        }
        this.scripts.add(scriptValue);
    }

    public void setScripts(List<String> scripts) {
        this.scripts = scripts;
    }

    public List<String> getScripts() {
        return scripts;
    }

    public HashSet<String> getRootTableNames() {
        return rootTableNames;
    }

    public void setRootTableNames(HashSet<String> rootTableNames) {
        this.rootTableNames = rootTableNames;
    }

    public void addRootTableName(String rootTableName) {
        if (!"".equalsIgnoreCase(rootTableName) && rootTableName != null) {
            this.rootTableNames.add(rootTableName);
        }
    }

    public void addRootTableName(HashSet<String> rootTableNames) {
        if (rootTableNames != null && rootTableNames.size() > 0) {
            this.rootTableNames.addAll(rootTableNames);
        }
    }

    public String getSelectSQL() {
        return selectSQL;
    }

    public void setSelectSQL(String selectSQL) {
        this.selectSQL = selectSQL;
    }

    public String getFromSQL() {
        return fromSQL;
    }

    public void setFromSQL(String fromSQL) {
        this.fromSQL = fromSQL;
    }

    public String getWhereSQL() {
        return whereSQL;
    }

    public void setWhereSQL(String whereSQL) {
        this.whereSQL = whereSQL;
    }

    public String getGroupSQL() {
        return groupSQL;
    }

    public void setGroupSQL(String groupSQL) {
        this.groupSQL = groupSQL;
    }

    public String getHavingSQL() {
        return havingSQL;
    }

    public void setHavingSQL(String havingSQL) {
        this.havingSQL = havingSQL;
    }

    public String getJoinConditionSQL() {
        return joinConditionSQL;
    }

    public void setJoinConditionSQL(String joinConditionSQL) {
        this.joinConditionSQL = joinConditionSQL;
    }

    public List<String> getExpressionFunctionSQL() {
        return expressionFunctionSQL;
    }

    public void setExpressionFunctionSQL(List<String> expressionFunctionSQL) {
        this.expressionFunctionSQL = expressionFunctionSQL;
    }
}
