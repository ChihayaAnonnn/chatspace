package top.iceclean.chatspace.group.server.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.iceclean.chatspace.cache.SpaceCache;
import top.iceclean.chatspace.group.mapper.SpaceMapper;
import top.iceclean.chatspace.group.server.SpaceService;
import top.iceclean.chatspace.infrastructure.constant.ResponseStatusEnum;
import top.iceclean.chatspace.infrastructure.po.Space;
import top.iceclean.chatspace.infrastructure.pojo.Response;
import top.iceclean.chatspace.infrastructure.vo.SpaceVO;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author : Ice'Clean
 * @date : 2022-10-27
 */
@Service
public class SpaceServiceImpl implements SpaceService {

    /** 空间映射 */
    private final SpaceMapper spaceMapper;
    /** 空间缓存 */
    private final SpaceCache spaceCache;

    @Autowired
    public SpaceServiceImpl(SpaceMapper spaceMapper, SpaceCache spaceCache) {
        this.spaceMapper = spaceMapper;
        this.spaceCache = spaceCache;
    }

    @Override
    public Response getSpaceList() {
        List<SpaceVO> spaceList = spaceMapper.selectList(new LambdaQueryWrapper<>())
                .stream().map(spaceCache::wrapSpace).collect(Collectors.toList());
        return new Response(ResponseStatusEnum.OK)
                .addData("spaceList", spaceList);
    }

    @Override
    public List<Integer> getSpaceIdList() {
        return spaceMapper.selectList(new LambdaQueryWrapper<Space>()
                .select(Space::getId))
                .stream().map(Space::getId).collect(Collectors.toList());
    }

    @Override
    public Space getSpaceData(int spaceId) {
        return spaceMapper.selectById(spaceId);
    }
}
