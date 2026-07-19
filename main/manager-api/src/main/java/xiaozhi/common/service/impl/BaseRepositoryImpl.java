package xiaozhi.common.service.impl;

import java.util.Collection;

import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.enums.SqlMethod;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import com.baomidou.mybatisplus.extension.repository.AbstractRepository;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;

/**
 * 基础 Repository 实现类，提供 baseMapper 字段及批量操作方法的默认实现
 * 用于适配 MyBatis Plus 3.5.9+ 中 ServiceImpl 被移除后的场景
 */
public abstract class BaseRepositoryImpl<M extends BaseMapper<T>, T> extends AbstractRepository<M, T> {

    @Autowired
    protected M baseMapper;

    @Override
    public M getBaseMapper() {
        return baseMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveBatch(Collection<T> entityList, int batchSize) {
        String sqlStatement = getSqlStatement(SqlMethod.INSERT_ONE);
        return SqlHelper.executeBatch(getEntityClass(), this.log, entityList, batchSize,
                (sqlSession, entity) -> sqlSession.insert(sqlStatement, entity));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateBatch(Collection<T> entityList, int batchSize) {
        return SqlHelper.executeBatch(getEntityClass(), this.log, entityList, batchSize, (sqlSession, entity) -> {
            Object idVal = ReflectionKit.getFieldValue(entity, "id");
            String sqlStatement;
            if (idVal == null) {
                sqlStatement = getSqlStatement(SqlMethod.INSERT_ONE);
                return sqlSession.insert(sqlStatement, entity);
            }
            sqlStatement = getSqlStatement(SqlMethod.UPDATE_BY_ID);
            MapperMethod.ParamMap<T> param = new MapperMethod.ParamMap<>();
            param.put(Constants.ENTITY, entity);
            return sqlSession.update(sqlStatement, param);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateBatchById(Collection<T> entityList, int batchSize) {
        String sqlStatement = getSqlStatement(SqlMethod.UPDATE_BY_ID);
        return SqlHelper.executeBatch(getEntityClass(), this.log, entityList, batchSize, (sqlSession, entity) -> {
            MapperMethod.ParamMap<T> param = new MapperMethod.ParamMap<>();
            param.put(Constants.ENTITY, entity);
            return sqlSession.update(sqlStatement, param);
        });
    }

    protected String getSqlStatement(SqlMethod sqlMethod) {
        return SqlHelper.getSqlStatement(getMapperClass(), sqlMethod);
    }
}
