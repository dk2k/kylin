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

package org.apache.kylin.job.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.lang.StringUtils;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.apache.kylin.common.KylinConfig;
import org.apache.kylin.common.StorageURL;
import org.apache.kylin.common.logging.LogOutputStream;
import org.apache.kylin.common.persistence.metadata.JdbcDataSource;
import org.apache.kylin.common.persistence.metadata.jdbc.JdbcUtil;
import org.apache.kylin.common.util.AddressUtil;
import org.apache.kylin.guava30.shaded.common.collect.Maps;
import org.apache.kylin.job.JobContext;
import org.apache.kylin.job.config.JobMybatisConfig;
import org.apache.kylin.job.config.JobTableInterceptor;
import org.apache.kylin.job.dao.JobInfoDao;
import org.apache.kylin.job.execution.ExecutableManager;
import org.apache.kylin.job.mapper.JobInfoMapper;
import org.apache.kylin.job.mapper.JobLockMapper;
import org.apache.kylin.rest.util.SpringContext;
import org.apache.kylin.tool.restclient.RestClient;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

/**
 * static fields are used by UT OR shell tool which does not have SpringContext
 */
@Slf4j
public class JobContextUtil {

    private static final Charset DEFAULT_CHARSET = Charset.defaultCharset();

    private static final String CREATE_JOB_INFO_TABLE = "create.job.info.table";

    private static final String CREATE_JOB_LOCK_TABLE = "create.job.lock.table";

    private static JobInfoMapper jobInfoMapper;

    private static JobLockMapper jobLockMapper;

    private static JobInfoDao jobInfoDao;

    private static JobContext jobContext;

    private static SqlSessionTemplate sqlSessionTemplate;

    private static DataSourceTransactionManager transactionManager;

    private static JobTableInterceptor jobTableInterceptor = new JobTableInterceptor();

    private static JobMybatisConfig jobMybatisConfig = new JobMybatisConfig();

    synchronized private static JobInfoDao getJobInfoDaoForTestOrTool(KylinConfig config) {
        initMappers(config);
        if (null == jobInfoDao) {
            jobInfoDao = new JobInfoDao();
            jobInfoDao.setJobInfoMapper(jobInfoMapper);
            jobInfoDao.setJobLockMapper(jobLockMapper);
        }
        return jobInfoDao;
    }

    synchronized private static JobContext getJobContextForTestOrTool(KylinConfig config) {
        initMappers(config);
        if (config.isUTEnv()) {
            config.setProperty("kylin.job.master-poll-interval-second", "1");
            config.setProperty("kylin.job.scheduler.poll-interval-second", "1");
        }
        if (null == jobContext) {
            jobContext = new JobContext();
            jobContext.setKylinConfig(config);
            jobContext.setJobInfoMapper(jobInfoMapper);
            jobContext.setJobLockMapper(jobLockMapper);
            jobContext.setTransactionManager(transactionManager);
            jobContext.init();
        }
        return jobContext;
    }

    synchronized private static void initMappers(KylinConfig config) {
        if (null != jobInfoMapper && null != jobLockMapper) {
            return;
        }
        boolean isUTEnv = config.isUTEnv();

        StorageURL url = config.getMetadataUrl();
        Properties props = JdbcUtil.datasourceParameters(url);
        DataSource dataSource = null;
        try {
            dataSource = JdbcDataSource.getDataSource(props);
            if (!isUTEnv) {
                // setup job table names
                jobMybatisConfig.setDataSource(dataSource);
                jobMybatisConfig.setupJobTables();
                jobTableInterceptor.setJobMybatisConfig(jobMybatisConfig);
            }
            transactionManager = JdbcDataSource.getTransactionManager(dataSource);
            SqlSessionFactory sqlSessionFactory = getSqlSessionFactory(dataSource);
            addPluginForSqlSessionManager(sqlSessionFactory);
            sqlSessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
            jobInfoMapper = sqlSessionTemplate.getMapper(JobInfoMapper.class);
            jobLockMapper = sqlSessionTemplate.getMapper(JobLockMapper.class);
            if (isUTEnv) {
                createTableIfNotExist((BasicDataSource) dataSource, "job_info");
                createTableIfNotExist((BasicDataSource) dataSource, "job_lock");
                jobInfoMapper.deleteAllJob();
                jobLockMapper.deleteAllJobLock();
            }
        } catch (Exception e) {
            throw new RuntimeException("initialize mybatis mappers failed", e);
        }
    }

    private static void addPluginForSqlSessionManager(SqlSessionFactory sqlSessionFactory) {
        List<Interceptor> interceptors = sqlSessionFactory.getConfiguration().getInterceptors();

        if (!interceptors.contains(jobTableInterceptor)) {
            sqlSessionFactory.getConfiguration().addInterceptor(jobTableInterceptor);
        }

    }

    public static SqlSessionFactory getSqlSessionFactory(DataSource dataSource) throws SQLException, IOException {
        log.info("Start to build data loading SqlSessionFactory");
        TransactionFactory transactionFactory = new SpringManagedTransactionFactory();
        Environment environment = new Environment("data loading", transactionFactory, dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setUseGeneratedKeys(true);
        configuration.setJdbcTypeForNull(JdbcType.NULL);
        configuration.addMapper(JobInfoMapper.class);
        configuration.addMapper(JobLockMapper.class);
        // configuration.setLogImpl(StdOutImpl.class);
        configuration.setCacheEnabled(false);
        setMapperXML(configuration);
        return new SqlSessionFactoryBuilder().build(configuration);
    }

    private static void setMapperXML(Configuration configuration) throws IOException {
        ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resourceResolver.getResources("classpath:/mybatis-mapper/*Mapper.xml");
        for (Resource resource : resources) {
            XMLMapperBuilder xmlMapperBuilder = new XMLMapperBuilder(resource.getInputStream(), configuration,
                    resource.toString(), configuration.getSqlFragments());
            xmlMapperBuilder.parse();
        }
    }

    private static void createTableIfNotExist(BasicDataSource dataSource, String tableName)
            throws IOException, SQLException {
        if (JdbcUtil.isTableExists(dataSource.getConnection(), tableName)) {
            log.info("{} already existed in database", tableName);
            return;
        }

        String createTableStmtProp = "";
        if ("job_info".equals(tableName)) {
            createTableStmtProp = CREATE_JOB_INFO_TABLE;
        } else {
            createTableStmtProp = CREATE_JOB_LOCK_TABLE;
        }

        Properties properties = JdbcUtil.getProperties(dataSource);
        String createTableStmt = String.format(Locale.ROOT, properties.getProperty(createTableStmtProp), tableName);
        try (Connection connection = dataSource.getConnection()) {
            ScriptRunner sr = new ScriptRunner(connection);
            sr.setLogWriter(new PrintWriter(new OutputStreamWriter(new LogOutputStream(log), DEFAULT_CHARSET)));
            log.debug("start to create table({})", tableName);
            sr.runScript(new InputStreamReader(new ByteArrayInputStream(createTableStmt.getBytes(DEFAULT_CHARSET)),
                    DEFAULT_CHARSET));
            log.debug("create table finished");
        }

        if (!JdbcUtil.isTableExists(dataSource.getConnection(), tableName)) {
            log.debug("failed to create table({})", tableName);
            throw new IllegalStateException(String.format(Locale.ROOT, "create table(%s) failed", tableName));
        } else {
            log.debug("table({}) already exists.", tableName);
        }
    }

    public static JobInfoDao getJobInfoDao(KylinConfig config) {
        if (config.isUTEnv() || isNoSpringContext()) {
            return getJobInfoDaoForTestOrTool(config);
        } else {
            return SpringContext.getBean(JobInfoDao.class);
        }
    }

    public static JobContext getJobContext(KylinConfig config) {
        if (config.isUTEnv() || isNoSpringContext()) {
            return getJobContextForTestOrTool(config);
        } else {
            return SpringContext.getBean(JobContext.class);
        }
    }

    public static DataSourceTransactionManager getTransactionManager(KylinConfig config) {
        if (config.isUTEnv() || isNoSpringContext()) {
            synchronized (JobContextUtil.class) {
                initMappers(config);
                return transactionManager;
            }
        } else {
            return SpringContext.getBean(DataSourceTransactionManager.class);
        }
    }

    private static boolean isNoSpringContext() {
        return null == SpringContext.getApplicationContext();
    }

    // for test only
    synchronized public static void cleanUp() {
        try {
            if (null != jobContext) {
                jobContext.destroy();
            }
            jobInfoMapper = null;
            jobLockMapper = null;
            jobInfoDao = null;
            jobContext = null;
            sqlSessionTemplate = null;
            transactionManager = null;
        } catch (Exception e) {
            log.error("JobContextUtil clean up failed.");
            throw new RuntimeException("JobContextUtil clean up failed.", e);
        }
    }

    public static Map<String, List<String>> splitJobIdsByScheduleInstance(List<String> ids) {
        Map<String, List<String>> nodeWithJobs = new HashMap<>();
        for (String jobId : ids) {
            String host = getJobSchedulerHost(jobId);
            List<String> jobIds = nodeWithJobs.getOrDefault(host, new ArrayList<>());
            jobIds.add(jobId);
            nodeWithJobs.put(host, jobIds);
        }
        return nodeWithJobs;
    }

    public static String getJobSchedulerHost(String jobId) {
        JobContext jobContext = getJobContext(KylinConfig.getInstanceFromEnv());
        String jobNode = jobContext.getJobLockMapper().findNodeByLockId(jobId);
        if (Objects.isNull(jobNode)) {
            return jobContext.getServerNode();
        }
        return jobNode;
    }

    @SneakyThrows
    public static void remoteDiscardJob(String project, List<String> jobIdList) {
        Map<String, List<String>> nodeWithJobs = JobContextUtil.splitJobIdsByScheduleInstance(jobIdList);
        String local = AddressUtil.getLocalInstance();
        for (Map.Entry<String, List<String>> entry : nodeWithJobs.entrySet()) {
            log.info("Discarding jobs {} on node {}", entry.getValue(), entry.getKey());
            if (local.equals(entry.getKey())) {
                val executableManager = ExecutableManager.getInstance(KylinConfig.getInstanceFromEnv(), project);
                entry.getValue().stream().forEach(jobId -> executableManager.discardJob(jobId));
                continue;
            }
            Map<String, String> form = Maps.newHashMap();
            form.put("project", project);
            form.put("jobId", StringUtils.join(entry.getValue(), ","));
            RestClient client = new RestClient(entry.getKey());
            client.forwardPostWithUrlEncodedForm("/job_delegate/discard_job", null, form);
        }
    }

    public static <T> T withTxAndRetry(JdbcUtil.Callback<T> consumer) {
        return withTxAndRetry(consumer, 3);
    }

    @SneakyThrows
    public static <T> T withTxAndRetry(JdbcUtil.Callback<T> consumer, int retryLimit) {
        return JdbcUtil.withTxAndRetry(getTransactionManager(KylinConfig.getInstanceFromEnv()), consumer, retryLimit);
    }
}
