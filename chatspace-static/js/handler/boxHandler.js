/** 箱子查看处理器 */
class BoxHandler {
    /** 箱子查询按钮 */
    #boxOpen; #itemList;
    /** 用户箱子列表服务 */
    #userItemListService;

    /** 用户随身空间箱 */
    #userBox; #userItemList;
    /** 箱子物品列表服务 */
    #boxItemService

    /** 箱子详情项服务 */
    #itemDetailService;

    /** 物品移动 */
    #moveOne; #moveAll;

    /** 本身 */
    #boxHandler;

    /** 用户 ID */
    #userId;

    constructor(userBox, userItemList, userItemListService,
                boxOpen, itemList, boxItemService, itemDetailService,
                moveOne, moveAll) {
        this.#userBox = userBox;
        this.#userItemList = userItemList;
        this.#userItemListService = userItemListService;

        this.#boxOpen = boxOpen;
        this.#itemList = itemList;
        this.#boxItemService = boxItemService;

        this.#itemDetailService = itemDetailService;

        this.#moveOne = moveOne;
        this.#moveAll = moveAll;

        this.#boxHandler = this;
        this.#userId = BoxHandler.#getUserId("userId");

        // 绑定用户打开空间箱事件
        this.#userBox.bind("click", () => this.#updateUserBox())
        // 绑定箱子打开事件
        this.#boxOpen.bind("click", () => this.#updateBox())
        // 为物品项列表绑定事件
        // this.#userItemList.bind("click", () => sc(this, this.#updateItemDetail))
        // this.#itemList.bind("click",  () => sc(this, this.#updateItemDetail))
        // 绑定物品移动事件
        this.#moveOne.bind("click", () => this.#moveItem(1))
        this.#moveAll.bind("click", () => this.#moveItem(-1))
    }

    static #getUserId(name) {
        let reg = new RegExp("(^|&)" + name + "=([^&]*)(&|$)", "i");
        let r = window.location.search.substr(1).match(reg);
        if (r != null) {
            return decodeURIComponent(r[2]);
        }
        return null;
    }

    #updateUserBox() {
        $.ajax({
            type: "GET",
            dataType: "json",
            headers: {"admin-tag": 1, "auth-user": this.#userId},
            url: "https://chatspace.iceclean.top/space/zone/box/user/list/item",
            success: (data) => {
                if (data.ok) {
                    // 将箱子内容加载到列表中
                    this.#userItemListService.setData({["boxId"]: data.data.boxId})
                    this.#userItemListService.setData({["userItemList"]: data.data.itemList})
                } else {
                    // 清空
                    alert(data.msg)
                    this.#userItemListService.setData({["boxId"]: ""})
                    this.#userItemListService.setData({["userItemList"]: ""})
                }
            }
        })
    }

    #updateBox() {
        $.ajax({
            type: "GET",
            dataType: "json",
            headers: {"admin-tag": 1, "auth-user": this.#userId},
            url: "https://chatspace.iceclean.top/space/zone/box/list/item",
            success: (data) => {
                if (data.ok) {
                    // 将箱子内容加载到列表中
                    this.#boxItemService.setData({["boxId"]: data.data.boxId})
                    this.#boxItemService.setData({["itemList"]: data.data.itemList})
                } else {
                    // 清空
                    alert(data.msg)
                    this.#boxItemService.setData({["boxId"]: ""})
                    this.#boxItemService.setData({["itemList"]: ""})
                }

            }
        })
    }

    #updateItemDetail() {
        sc(this.#boxHandler, this.#boxHandler.updateD)
    }

    updateD() {
        // 首先获取 itemDetailService 中的数据
        let boxItemId = this.getData(this.#itemDetailService, "boxItemId")
        // 查询物品项详情
        $.ajax({
            type: "GET",
            dataType: "json",
            headers: {"admin-tag": 1, "auth-user": this.#userId},
            url: "https://chatspace.iceclean.top/space/zone/box/item/detail/" + boxItemId,
            success: (data) => {
                // 写入详细信息
                this.#itemDetailService.setData({["item"]: data.data})
            }
        })
    }

    #moveItem(num) {
        // 获取当前的物品项 ID
        let boxItemId = this.getData(this.#itemDetailService).boxItemId;
        if (boxItemId === undefined) {
            alert("当前没有选中任何物品！")
        } else {
            $.ajax({
                type: "PATCH",
                dataType: "json",
                headers: {"admin-tag": 1, "auth-user": this.#userId},
                url: "https://chatspace.iceclean.top/space/zone/box/move/" + boxItemId + "/" + num,
                success: (data) => {
                    if (data.ok) {
                        // 更新随身背包和空间箱
                        this.#updateUserBox();
                        this.#updateBox();
                        this.#itemDetailService.setData({})
                    } else {
                        alert(data.msg)
                    }
                }
            })
        }
    }

    getData(service, dataKey) {
        return dataKey === undefined ?
            service._reactInternals.memoizedProps.data :
            service._reactInternals.memoizedProps.data[dataKey]
    }
}