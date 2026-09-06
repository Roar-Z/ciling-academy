package com.wordspirit.module.game.service;

import com.wordspirit.module.game.dto.GameRecordReq;
import com.wordspirit.module.game.entity.GameRecord;

import java.util.List;
import java.util.Map;

/**
 * 游戏服务
 */
public interface GameService {

    /** 保存游戏记录并发放金币（金币由后端按正确率计算，防止前端伪造） */
    Map<String, Object> saveRecord(Long userId, GameRecordReq req);

    /** 我的游戏记录 */
    Object myRecords(Long userId, String gameId, long page, long size);

    /** 简单排行榜（按最高分排序，默认取前10） */
    List<Map<String, Object>> rank(String gameId, int limit);

    /** 我的游戏统计（最高分、总次数、平均正确率） */
    Map<String, Object> myStats(Long userId, String gameId);
}
