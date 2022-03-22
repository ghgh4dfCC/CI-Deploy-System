/** layuiAdmin.std-v1.2.1 LPPL License By http://www.layui.com/admin/ */
;
layui.define(["table", "form", "element"], function(e) {
	var t = layui.$,
		i = layui.table,
		r = (layui.form, layui.element);
	i.render({
		elem: "#LAY-app-system-order",
		url: layui.setter.base + "json/workorder/demo.js",
		cols: [
			[{
				type: "numbers",
				fixed: "left"
			}, {
				field: "orderid",
				width: 100,
				title: "工单号",
				sort: !0
			}, {
				field: "attr",
				width: 100,
				title: "业务性质"
			}, {
				field: "title",
				width: 100,
				title: "工单标题",
				width: 300
			}, {
				field: "progress",
				title: "进度",
				width: 200,
				align: "center",
				templet: "#progressTpl"
			}, {
				field: "submit",
				width: 100,
				title: "提交者"
			}, {
				field: "accept",
				width: 100,
				title: "受理人员"
			}, {
				field: "state",
				title: "工单状态",
				templet: "#buttonTpl",
				minWidth: 80,
				align: "center"
			}, {
				title: "操作",
				align: "center",
				fixed: "right",
				toolbar: "#table-system-order"
			}]
		],
		page: !0,
		limit: 10,
		limits: [10, 15, 20, 25, 30],
		text: "对不起，加载出现异常！",
		done: function() {
			r.render("progress")
		}
	}), i.on("tool(LAY-app-system-order)", function(e) {
		e.data;
		if ("edit" === e.event) {
			t(e.tr);
			layer.open({
				type: 2,
				title: "编辑工单",
				content: "../../../views/app/workorder/listform.html",
				area: ["450px", "450px"],
				btn: ["确定", "取消"],
				yes: function(e, t) {
					var r = window["layui-layer-iframe" + e],
						l = "LAY-app-workorder-submit",
						o = t.find("iframe").contents().find("#" + l);
					r.layui.form.on("submit(" + l + ")", function(t) {
						t.field;
						i.reload("LAY-user-front-submit"), layer.close(e)
					}), o.trigger("click")
				},
				success: function(e, t) {}
			})
		}
	}), e("workorder", {})
});