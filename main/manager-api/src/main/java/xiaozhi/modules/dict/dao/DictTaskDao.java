package xiaozhi.modules.dict.dao;

import org.apache.ibatis.annotations.Mapper;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.dict.entity.DictTaskEntity;

@Mapper
public interface DictTaskDao extends BaseDao<DictTaskEntity> {
}
