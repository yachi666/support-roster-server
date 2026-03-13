package com.support.server.supportrosterserver.config;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.baomidou.mybatisplus.core.config.GlobalConfig;

@Configuration
@MapperScan("com.support.server.supportrosterserver.mapper")
public class MybatisPlusConfig {

	@Bean
	public MybatisPlusInterceptor mybatisPlusInterceptor() {
		return new MybatisPlusInterceptor();
	}

	@Bean
	public SqlSessionFactory sqlSessionFactory(DataSource dataSource, MybatisPlusInterceptor interceptor, GlobalConfig globalConfig) throws Exception {
		MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
		factoryBean.setDataSource(dataSource);

		MybatisConfiguration configuration = new MybatisConfiguration();
		configuration.setMapUnderscoreToCamelCase(true);
		factoryBean.setConfiguration(configuration);
		factoryBean.setPlugins(interceptor);
		factoryBean.setGlobalConfig(globalConfig);
		return factoryBean.getObject();
	}

	@Bean
	public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
		return new SqlSessionTemplate(sqlSessionFactory);
	}

	@Bean
	public GlobalConfig globalConfig(MyMetaObjectHandler myMetaObjectHandler) {
		GlobalConfig globalConfig = new GlobalConfig();
		globalConfig.setMetaObjectHandler(myMetaObjectHandler);
		GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
		dbConfig.setLogicDeleteField("deleted");
		dbConfig.setLogicDeleteValue("1");
		dbConfig.setLogicNotDeleteValue("0");
		globalConfig.setDbConfig(dbConfig);
		return globalConfig;
	}
}