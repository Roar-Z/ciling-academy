package com.wordspirit.module.game.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wordspirit.module.game.entity.GameRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 游戏记录 Mapper
 */
@Mapper
public interface GameRecordMapper extends BaseMapper<GameRecord> {
}
