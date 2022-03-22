;
layui.define(["table", "form"],
function(t) {
    var $ = layui.$,
    i = layui.table,
    n = layui.form;

    i.render({
        elem: "#LAY-app-content-list",
        url: "/deploy/getdeployapplist",
        method: 'post',
        //本系统ajax异步请求都统一用post请求
        cols: [[{
            type: "checkbox",
            fixed: "left"
        },
        {
            field: "id",
            title: "ID",
            hide: true
        },
        {
            field: "appName",
            title: "应用名称",
            width: 260
        },
        {
            field: "fileDir",
            title: "文件目录",
            width: 500
        },
        {
            field: "projectName",
            title: "所属项目",
            width: 260
        },
        {
            field: "description",
            title: "描述"
        },
        {
            title: "操作",
            align: "center",
            width: 200,
            fixed: "right",
            toolbar: "#table-content-list"
        }]],
        page: !0,
        limit: 15,
        limits: [10, 15, 20, 25, 30],
        text: {
            none: '暂无相关数据' //默认：无数据。
        }
    }),
    i.on("tool(LAY-app-content-list)",
    function(t) {
        var e = t.data;
        var id = e.id;
        if ("del" === t.event) {
            layer.confirm("确定删除此应用？",
            function(e) {
                var arrayIds = new Array();
                arrayIds.push(id);

                $.ajax({
                    type: "POST",
                    url: "/deploy/app/delete",
                    data: {
                        ids: arrayIds
                    },
                    dataType: "json"
                }).done(function(result) {
                    if (result.error == 0) {
                        //执行 Ajax 后重载         
                        i.reload('LAY-app-content-list', {
                            where: { //设定异步数据接口的额外参数，任意设
                                appName: null
                            },
                            page: {
                                curr: 1 //重新从第 1 页开始
                            }
                        });
                        layer.msg('删除成功');
                    } else {
                        layer.msg(result.msg, {
                            offset: '15px',
                            icon: 2,
                            time: 3000
                        });
                    }
                });
            });
        }

        if ("editApp" === t.event) {
            layer.open({
                type: 2,
                title: '编辑应用',
                content: '/deploy/app/edit?id=' + id + '&v=' + Math.random(),
                maxmin: true
                ,area: ['800px', '650px']
                ,btn: ['确定', '取消'],
                yes: function(index, layero) {
                    var editiframe = window["layui-layer-iframe" + index],
                    a = layero.find("iframe").contents().find("#layuiadmin-app-form-edit");
                    editiframe.layui.form.on("submit(layuiadmin-app-form-edit)",
                    function(i) {
//                        t.update({
//                            name: i.field.name,
//                            show: i.field.show
//                        });

                        $.ajax({
                            type: "POST",
                            url: "/deploy/app/edit",
                            data: i.field,
                            dataType: "json"
                        }).done(function(result) {
                            if (result.error == 0) {
                                layer.msg('编辑成功', {
                                    offset: '15px',
                                    icon: 1,
                                    time: 1000
                                },
                                function() {
                                    layui.table.reload('LAY-app-content-list', {
                                        where: {
                                            appName: null
                                        },
                                        page: {
                                            curr: 1 //重新从第 1 页开始
                                        }
                                    });
                                    layer.close(index); //再执行关闭
                                });
                            } else {
                                layer.msg(result.msg, {
                                    offset: '15px',
                                    icon: 2,
                                    time: 3000
                                });
                            }
                        });
                    }),
                    a.trigger("click");
                }
            });

        }

    }),
    t("applist", {})
});