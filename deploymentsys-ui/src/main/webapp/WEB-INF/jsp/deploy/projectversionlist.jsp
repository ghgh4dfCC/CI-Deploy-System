<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>项目版本列表</title>
  <meta name="renderer" content="webkit">
  <meta http-equiv="Pragma" content="no-cache" />
  <meta http-equiv="cache-control" content="no-cache, must-revalidate">
  <meta http-equiv="expires" content="0">
  
  <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, minimum-scale=1.0, maximum-scale=1.0, user-scalable=0">
  <link rel="stylesheet" href="/resources/layuiadmin/layui/css/layui.css" media="all">
  <link rel="stylesheet" href="/resources/layuiadmin/style/admin.css" media="all">
</head>
<body>
  <div class="layui-fluid">
    <div class="layui-card">
      <div class="layui-form layui-card-header layuiadmin-card-header-auto">
        <div class="layui-form-item">
          <div class="layui-inline">
           <span class="layui-breadcrumb">
			  <a href="javascript:void(0);">${projectName}</a>
			  <a><cite>项目版本</cite></a>
			</span>
          </div>
        </div>
      </div>

<input type="hidden" id="projectId" value="${projectId}">
      <div class="layui-card-body">
        <div style="padding-bottom: 10px;">
          <button class="layui-btn layuiadmin-btn-list" data-type="batchdel">删除</button>
          <button class="layui-btn layuiadmin-btn-list" data-type="add">添加</button>
        </div>
        <table id="LAY-app-content-list" lay-filter="LAY-app-content-list"></table> 

        <script type="text/html" id="table-content-list">
          <a class="layui-btn layui-btn-normal layui-btn-xs" lay-event="edit"><i class="layui-icon layui-icon-edit"></i>编辑</a>
          <a class="layui-btn layui-btn-danger layui-btn-xs" lay-event="del"><i class="layui-icon layui-icon-delete"></i>删除</a>
        </script>
      </div>
    </div>
  </div>

  <script src="/resources/layuiadmin/layui/layui.js"></script>  
  <script>
  layui.config({
	    base: '/resources/layuiadmin/', //静态资源所在路径
	    version: '6c1c7224fd9344e5b1d927a1c8a566ef'
	}).extend({
	    index: 'lib/index',
	    //主入口模块
	    projectversionlist: 'deploy/projectversionlist'
	}).use(['index', 'projectversionlist', 'table'],
	function() {
	    var table = layui.table,
	    form = layui.form;

	    var $ = layui.$,
	    active = {
	        batchdel: function() {
	            var checkStatus = table.checkStatus('LAY-app-content-list'),
	            checkData = checkStatus.data; //得到选中的数据
	            if (checkData.length === 0) {
	                return layer.msg('请选择数据');
	            }

	            layer.confirm('确定删除所选数据吗？',
	            function(index) {
	                var arrayIds = new Array();
	                $.each(checkData,
	                function(index, value) {
	                    arrayIds.push(value.id);
	                });

	                $.ajax({
	                    type: "POST",
	                    url: "/deploy/projectversion/delete",
	                    data: {
	                        ids: arrayIds
	                    },
	                    dataType: "json"
	                }).done(function(result) {
	                    if (result.error == 0) {
	                        //执行 Ajax 后重载         
	                        table.reload('LAY-app-content-list', {
	                            where: { //设定异步数据接口的额外参数，任意设
	                            	projectId: '${projectId}'
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
	                            time: 1000
	                        });
	                    }
	                });
	            });
	        },
	        add: function() {
	            layer.open({
	                type: 2,
	                title: '添加版本',
	                content: '/deploy/projectversion/add?projectId=' + '${projectId}' + '&v=' + Math.random(),
	                maxmin: true,
	                area: ['650px', '450px'],
	                btn: ['确定', '取消'],
	                yes: function(index, layero) {
	                    //点击确认触发 iframe 内容中的按钮提交
	                    var submit = layero.find('iframe').contents().find("#layuiadmin-app-form-submit");
	                    submit.click();
	                }
	            });
	        }
	    };

	    $('.layui-btn.layuiadmin-btn-list').on('click',
	    function() {
	        var type = $(this).data('type');
	        active[type] ? active[type].call(this) : '';
	    });

	});
  </script>
</body>
</html>    