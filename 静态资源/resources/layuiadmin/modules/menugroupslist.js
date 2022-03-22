;
layui.define(["table", "form"], function(t) {
	var $ = layui.$,
		i = layui.table,
		n = layui.form;
	i.render({
		elem: "#LAY-app-content-list",
		url: "/menugroup/listdata",
		method:'post',//本系统ajax异步请求都统一用post请求
		cols: [
			[{
				type: "checkbox",
				fixed: "left"
			}, {
				field: "id",
				width: 100,
				title: "ID",
				hide: true
			}, {
				field: "name",
				title: "功能组名称",
				minWidth: 100
			}, {
				field: "show",
				title: "是否在左侧菜单显示",
				templet: "#buttonTpl",
				minWidth: 80,
				align: "center"
			}, {
				field: "sort",
				title: "排序",
				minWidth: 80
			}, {
				field: "createDate",
				title: "创建时间",
				minWidth: 100
			}, {
				title: "操作",
				minWidth: 150,
				align: "center",
				fixed: "right",
				toolbar: "#table-content-list"
			}]
		],
		page: !0,
		limit: 15,
		limits: [10, 15, 20, 25, 30],
		text: {
		    none: '暂无相关数据' //默认：无数据。
		  }
	}), i.on("tool(LAY-app-content-list)", function(t) {
		var e = t.data;
		var id=e.id;
		if ("del" === t.event) {
			layer.confirm("确定删除此功能组？", function(e) {
				console.log("单个删除id:"+ id);
				
				var arrayIds = new Array();
		        arrayIds.push(id);
		          
		          $.ajax({
						type: "POST",
						url: "/menugroup/delete",
						data: {ids: arrayIds},
						dataType: "json"
					}).done(
					function(result) {
						if (result.error == 0) {
							//执行 Ajax 后重载         
							i.reload('LAY-app-content-list', {
					        	  where: { //设定异步数据接口的额外参数，任意设
					        		  name: null
					        		  }
					        		  ,page: {
					        		    curr: 1 //重新从第 1 页开始
					        		  }
					        		});
					        layer.msg('删除成功');
						}else {
							layer.msg(result.msg, {
					            offset: '15px'
					            ,icon: 2
					            ,time: 1000
					          });
						}		
					});
			});
		}
		
		if ("editMenuGroup" === t.event) {
			layer.open({
		          type: 2
		          ,title: '编辑功能组'
		          ,content: '/menugroup/editmenugroup?id=' + id + '&v='+Math.random()
		          ,maxmin: true
		          ,area: ['550px', '450px']
		          ,btn: ['确定', '取消']
		          ,yes: function(index, layero){
		        	  var editiframe = window["layui-layer-iframe" + index],
						a = layero.find("iframe").contents().find("#layuiadmin-app-form-edit");
		        	  editiframe.layui.form.on("submit(layuiadmin-app-form-edit)", function(i) {
						t.update({
							name: i.field.name,
							show: i.field.show
						});
						
						$.ajax({
				  			type: "POST",
				  			url: "/menugroup/editmenugroup",
				  			data: i.field,
				  			dataType: "json"
				  		}).done(
				  		function(result) {
				  			console.log(result);
				  			if (result.error==0) {
				  				layer.msg('编辑成功', {
				  				            offset: '15px'
				  				            ,icon: 1
				  				            ,time: 1000
				  				          },function(){
				  				        	layui.table.reload('LAY-app-content-list', {
									        	  where: {
									        		  name: null
									        		  }
									        		  ,page: {
									        		    curr: 1 //重新从第 1 页开始
									        		  }
									        		});
				  				        	layer.close(index); //再执行关闭
				  				          });
				  			}else {
								layer.msg(result.msg, {
						            offset: '15px'
						            ,icon: 2
						            ,time: 1000
						          });
							}
				  		});
					}), a.trigger("click");
		          }
		        });
		}
		
		if ("getPermissions" === t.event) {
			layer.open({
		          type: 2
		          ,title: '查看所包含的功能'
		          ,content: '/menugroup/showmenusofmenugroup?id=' + id + '&v='+Math.random()
		          ,maxmin: true
		          ,area: ['550px', '750px']		          
		        });
		}
		
		
	}), t("menugroupslist", {})
});