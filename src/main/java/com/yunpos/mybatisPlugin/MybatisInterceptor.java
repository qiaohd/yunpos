package com.yunpos.mybatisPlugin;

import java.sql.Connection;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.reflection.wrapper.ObjectWrapperFactory;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.yunpos.model.DataRule;
import com.yunpos.translation.FilterGroup;
import com.yunpos.translation.FilterTranslator;

@Intercepts({
		@Signature(type = StatementHandler.class, method = "prepare", args = { Connection.class }) })
public class MybatisInterceptor implements Interceptor {
	private Properties properties;
	
    private static final ObjectFactory DEFAULT_OBJECT_FACTORY = new DefaultObjectFactory();  
    private static final ObjectWrapperFactory DEFAULT_OBJECT_WRAPPER_FACTORY = new DefaultObjectWrapperFactory();  

	public Object intercept(Invocation invocation) throws Throwable {
		
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();  
        MetaObject metaStatementHandler = MetaObject.forObject(statementHandler, DEFAULT_OBJECT_FACTORY,  
                DEFAULT_OBJECT_WRAPPER_FACTORY);  
        // 分离代理对象链  
        while (metaStatementHandler.hasGetter("h")) {  
            Object object = metaStatementHandler.getValue("h");  
            metaStatementHandler = MetaObject.forObject(object, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY);  
        }  
        // 分离最后一个代理对象的目标类  
        while (metaStatementHandler.hasGetter("target")) {  
            Object object = metaStatementHandler.getValue("target");  
            metaStatementHandler = MetaObject.forObject(object, DEFAULT_OBJECT_FACTORY, DEFAULT_OBJECT_WRAPPER_FACTORY);  
        }  
        String originalSql = (String) metaStatementHandler.getValue("delegate.boundSql.sql");  
        Configuration configuration = (Configuration) metaStatementHandler.getValue("delegate.configuration");  
        Object parameterObject = metaStatementHandler.getValue("delegate.boundSql.parameterObject"); 

		HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
				.getRequest();
		DataRule dataRule = (DataRule) request.getAttribute("DATA_RULE");

		if (dataRule != null) {
			String rule = dataRule.getDatarule();
			System.out.println("rule:" + rule);
			ObjectMapper objectMapper = new ObjectMapper();
			ObjectReader reader = objectMapper.reader(FilterGroup.class);
			FilterGroup fg = reader.readValue(rule);
			FilterTranslator ft = new FilterTranslator("", fg);

			DataRule orderBy = SqlHelper.getOrderBy();
			Object parameter = null;
			if (invocation.getArgs().length > 1) {
				parameter = invocation.getArgs()[1];
			}

			if (null != originalSql && !"".equals(originalSql)) {  
	            String newSql  = ft.getOperatorQueryText(originalSql);;  
	            MappedStatement mappedStatement = (MappedStatement) metaStatementHandler  
	                    .getValue("delegate.mappedStatement");  
	            // 根据ID生成相应类型的sql语句（id需剔除namespace信息）  
	            String id = mappedStatement.getId();  
	            id = id.substring(id.lastIndexOf(".") + 1);  
	            //  
	            SqlSource sqlSource = buildSqlSource(configuration, newSql,parameterObject==null?Object.class: parameterObject.getClass());  
	            List<ParameterMapping> parameterMappings = sqlSource.getBoundSql(parameterObject).getParameterMappings();  
	            metaStatementHandler.setValue("delegate.boundSql.sql", sqlSource.getBoundSql(parameterObject).getSql());  
	            metaStatementHandler.setValue("delegate.boundSql.parameterMappings", parameterMappings);  
	        }  
	        // 调用原始statementHandler的prepare方法  
	        statementHandler = (StatementHandler) metaStatementHandler.getOriginalObject();  
	        statementHandler.prepare((Connection) invocation.getArgs()[0]); 

		}
		Object returnValue = null;
		// long start = System.currentTimeMillis();
		returnValue = invocation.proceed();
		// long end = System.currentTimeMillis();
		// long time = (end - start);
		// if (time > 1) {
		// String sql = getSql(configuration, boundSql, sqlId, time);
		// System.err.println(sql);
		// }
		return returnValue;
	}
	
	private SqlSource buildSqlSource(Configuration configuration, String originalSql,   
		    Class<?> parameterType) {  
		        SqlSourceBuilder builder = new SqlSourceBuilder(configuration);  
		        return builder.parse(originalSql, parameterType, null);  
		    }  

	public static String getSql(Configuration configuration, BoundSql boundSql, String sqlId, long time) {
		String sql = showSql(configuration, boundSql);
		StringBuilder str = new StringBuilder(100);
		str.append(sqlId);
		str.append(":");
		str.append(sql);
		str.append(":");
		str.append(time);
		str.append("ms");
		return str.toString();
	}

	private static String getParameterValue(Object obj) {
		String value = null;
		if (obj instanceof String) {
			value = "'" + obj.toString() + "'";
		} else if (obj instanceof Date) {
			DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.CHINA);
			value = "'" + formatter.format(new Date()) + "'";
		} else {
			if (obj != null) {
				value = obj.toString();
			} else {
				value = "";
			}
		}
		return value;
	}

	public static String showSql(Configuration configuration, BoundSql boundSql) {
		Object parameterObject = boundSql.getParameterObject();
		List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
		String sql = boundSql.getSql().replaceAll("[\\s]+", " ");
		if (parameterMappings.size() > 0 && parameterObject != null) {
			TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
			if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
				sql = sql.replaceFirst("\\?", getParameterValue(parameterObject));
			} else {
				MetaObject metaObject = configuration.newMetaObject(parameterObject);
				for (ParameterMapping parameterMapping : parameterMappings) {
					String propertyName = parameterMapping.getProperty();
					if (metaObject.hasGetter(propertyName)) {
						Object obj = metaObject.getValue(propertyName);
						sql = sql.replaceFirst("\\?", getParameterValue(obj));
					} else if (boundSql.hasAdditionalParameter(propertyName)) {
						Object obj = boundSql.getAdditionalParameter(propertyName);
						sql = sql.replaceFirst("\\?", getParameterValue(obj));
					}
				}
			}
		}
		return sql;
	}

	public Object plugin(Object target) {
		return Plugin.wrap(target, this);
	}

	public void setProperties(Properties properties0) {
		this.properties = properties0;
	}
}
