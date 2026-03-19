package lappick.common.service;

import org.springframework.stereotype.Service;

import lappick.common.mapper.AutoNumMapper;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AutoNumService {
    private final AutoNumMapper autoNumMapper;

    public String execute(String tableName, String colName, String preFix) {
        String autoNum = autoNumMapper.AutoNumSelect(tableName, colName, preFix);
        return autoNum;
    }

    public String nextIdFromSequence(String sequenceName, String prefix) {
        return autoNumMapper.selectNextPrefixedId(sequenceName, prefix);
    }
}
