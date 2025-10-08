package top.iceclean.chatspace.realtime.share;

import top.iceclean.chatspace.infrastructure.dto.SiteDTO;
import top.iceclean.chatspace.infrastructure.po.Space;
import top.iceclean.chatspace.infrastructure.vo.SiteVO;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 空间单元
 * @author : Ice'Clean
 * @date : 2022-10-01
 * @deprecated 应该使用 SpaceCache 来存取空间数据
 */
@Deprecated
public class SpaceUnit {
    /** 所有的空间元数据（用来进行空间的懒加载和元数据获取） */
    private static final ConcurrentHashMap<Integer, Space> SPACE_META_MAP = new ConcurrentHashMap<>();
    /** 所有的空间（可能存在未初始化的空间，当需要用到但为空时，使用元数据进行初始化） */
    private static final ConcurrentHashMap<Integer, SpaceUnit> SPACE_MAP = new ConcurrentHashMap<>();
    /** 所有的用户坐标 */
    private static final ConcurrentHashMap<Integer, SiteVO> SITE_MAP = new ConcurrentHashMap<>();

    // 初始化空间元信息
    static {
        // TODO 从数据库获取所有的空间信息
        System.out.println("初始化空间元数据");
        Space space = new Space();
        space.setId(0);
        space.setSpaceName("测试空间");
        space.setSizeX(20);
        space.setSizeY(20);
        SPACE_META_MAP.put(space.getId(), space);
    }

    /** 空间网格 */
    private GridUnit[][] spaceGrid;

    /** 空间的 ID、名称和横纵向大小 */
    private Integer spaceId;
    private String spaceName;
    private Integer sizeX;
    private Integer sizeY;

    /**
     * 初始化一个空间
     * @param space 空间实体
     */
    public SpaceUnit(Space space) {
        System.out.println("初始化空间：" + space);
        this.spaceId = space.getId();
        this.spaceName = space.getSpaceName();
        this.sizeX = space.getSizeX();
        this.sizeY = space.getSizeY();
        initSpaceGrid();
        SPACE_MAP.put(space.getId(), this);
    }

    /** 初始化空间网格 */
    private void initSpaceGrid() {
        spaceGrid = new GridUnit[sizeY][sizeX];
        for (int y = 0; y < sizeY; y++) {
            for (int x = 0; x < sizeX; x++) {
                spaceGrid[y][x] = new GridUnit(spaceId, x, y, 0);
            }
        }
    }

    /** 判断坐标是否合法 */
    public boolean isLegal(int x, int y) {
        return x >= 0 && x < sizeX && y >= 0 && y < sizeY;
    }

    /** 获取空间网格 */
    public GridUnit[][] getSpaceGrid() {
        return spaceGrid;
    }

    /** 获取网格单元 */
    public GridUnit getGrid(int x, int y) {
        return spaceGrid[y][x];
    }

    /** 获取用户所在的网格单元 */
    public static GridUnit getGrid(int userId) {
        SiteVO site = SITE_MAP.get(userId);
        return getSpaceUnit(site.getSpaceId()).spaceGrid[site.getY()][site.getX()];
    }

    /** 获取指定空间的指定网格 */
    public static GridUnit getGrid(int spaceId, int x, int y) {
        return getSpaceUnit(spaceId).spaceGrid[y][x];
    }

    /** 获取空间网格 */
    public static GridUnit[][] getSpaceGrid(int spaceId) {
        return getSpaceUnit(spaceId).spaceGrid;
    }

    /** 获取空间单元 */
    public static SpaceUnit getSpaceUnit(int spaceId) {
        // 存在该空间则直接返回
        if (SPACE_MAP.containsKey(spaceId)) {
            return SPACE_MAP.get(spaceId);
        }
        // 否则初始化完空间后，再返回
        SpaceUnit spaceUnit = new SpaceUnit(SPACE_META_MAP.get(spaceId));
        SPACE_MAP.put(spaceId, spaceUnit);
        return spaceUnit;
    }

    /** 获取空间元数据 */
    public static Space getSpace(int spaceId) {
        return SPACE_META_MAP.get(spaceId);
    }

    /** 获取用户坐标 */
    public static SiteVO getSite(int userId) {
        return SITE_MAP.get(userId);
    }

    /** 更新用户坐标，并返回用户原先所在的网格 */
    public static GridUnit updateSite(int userId, SiteDTO siteDTO) {
        GridUnit grid = getGrid(userId);
        SITE_MAP.get(userId).updateSite(siteDTO);
        return grid;
    }

    /** 用户初次抵达空间 */
    public static GridUnit newToSpace(int userId, SiteVO siteVO) {
        SITE_MAP.put(userId, siteVO);
        return getGrid(userId);
    }

    /** 用户离开空间 */
    public static void exitSpace(int userId) {
        SITE_MAP.remove(userId);
    }
}
