package xiaozhi.modules.dict.dao;

import org.apache.ibatis.annotations.Mapper;

import xiaozhi.common.dao.BaseDao;
import xiaozhi.modules.dict.entity.BizVocabularyEntity;

@Mapper
public interface BizVocabularyDao extends BaseDao<BizVocabularyEntity> {
}
