// 生成 ID
id = (new Date()).valueOf()
// 进入指标
$.ajax({
    type: "POST",
    dataType: "json",
    url: "https://iceclean.top/metric/in",
    data: JSON.stringify({id: id, path: window.location.pathname}),
    success: (data) => callback(data)
})

// 离开指标
window.onbeforeunload=function(e){
    $.ajax({
        type: "POST",
        dataType: "json",
        url: "https://iceclean.top/metric/exit",
        data: JSON.stringify({id: id, path: window.location.pathname}),
        success: (data) => callback(data)
    })
}