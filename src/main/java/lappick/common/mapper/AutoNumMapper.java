package lappick.common.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AutoNumMapper {
    public String AutoNumSelect(
        @Param("tableName") String tableName, 
        @Param("colName") String colName, 
        @Param("preFix") String preFix);

    public String selectNextPrefixedId(
        @Param("sequenceName") String sequenceName,
        @Param("prefix") String prefix);
}
