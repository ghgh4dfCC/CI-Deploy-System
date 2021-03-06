package com.deploymentsys.service.deploy;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson.JSONObject;
import com.deploymentsys.beans.BaseBean;
import com.deploymentsys.beans.constants.DeployTaskStatus;
import com.deploymentsys.beans.constants.SysConstants;
import com.deploymentsys.beans.deploy.DeployAppBean;
import com.deploymentsys.beans.deploy.DeployConfigBean;
import com.deploymentsys.beans.deploy.DeployFlowBean;
import com.deploymentsys.beans.deploy.DeployFlowServerBean;
import com.deploymentsys.beans.deploy.DeployFlowServerToDoBean;
import com.deploymentsys.beans.deploy.DeployTaskBean;
import com.deploymentsys.beans.deploy.DeployTaskFileBean;
import com.deploymentsys.beans.deploy.DeployTaskServerBean;
import com.deploymentsys.beans.deploy.DeployTaskServerToDoBean;
import com.deploymentsys.manager.deploy.DeployFlowManager;
import com.deploymentsys.manager.deploy.DeployFlowServerManager;
import com.deploymentsys.manager.deploy.DeployFlowServerToDoManager;
import com.deploymentsys.manager.deploy.DeployLogManager;
import com.deploymentsys.manager.deploy.DeployTaskFileManager;
import com.deploymentsys.manager.deploy.DeployTaskManager;
import com.deploymentsys.manager.deploy.DeployTaskServerManager;
import com.deploymentsys.manager.deploy.DeployTaskServerToDoManager;
import com.deploymentsys.utils.DateUtil;
import com.deploymentsys.utils.FileUtil;
import com.deploymentsys.utils.WebUtils;
import com.deploymentsys.utils.dtree.CheckArr;
import com.deploymentsys.utils.dtree.DTree;
import com.deploymentsys.utils.dtree.DTreeResponse;
import com.deploymentsys.utils.dtree.Status;

@Service
public class DeployService {

	private static final Logger LOGGER = LoggerFactory.getLogger(DeployService.class);
	private static ConcurrentLinkedQueue<String> deployQueue = new ConcurrentLinkedQueue<String>();

	/**
	 * ??????????????????????????????
	 */
	private static ExecutorService deployTaskThreadPool = Executors
			.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);

	@Autowired
	private DeployTaskFileManager deployTaskFileManager;
	@Autowired
	private DeployTaskManager deployTaskManager;
	@Autowired
	private DeployTaskServerManager taskServerManager;
	@Autowired
	private DeployTaskServerToDoManager taskServerToDoManager;

	@Autowired
	private DeployLogManager logManager;

	@Autowired
	private DeployFlowManager deployFlowManager;
	@Autowired
	private DeployFlowServerManager flowServerManager;
	@Autowired
	private DeployFlowServerToDoManager flowServerToDoManager;
	@Autowired
	private DeploySysConfigService deploySysConfigService;
	@Autowired
	private WebUtils webUtils;

	@Autowired
	DeployTaskService deployTaskService;

	/**
	 * ?????????????????????????????????
	 */
	// public void startupDeployMonitoring() {
	// while (true) {
	// try {
	// Thread.sleep(3000);
	// } catch (InterruptedException ex) {
	// LOGGER.error("startupDeployMonitoring????????????", ex);
	// }
	//
	// // ????????????????????? ?????????????????? ?????????
	// List<DeployTaskBean> tasks =
	// deployTaskManager.listByStatus(DeployTaskStatus.WAIT_FOR_EXEC_DEPLOY, 10);
	// LOGGER.info("??????{}??????????????????", tasks.size());
	//
	// if (tasks.size() > 0) {
	// int creatorId = 1;// ????????????????????????id
	// String currDate = webUtils.getCurrentDateStr();
	// String clientIp = "";
	// try {
	// clientIp = webUtils.getLocalIp();
	// } catch (Exception ex) {
	// // e.printStackTrace();
	// LOGGER.error("webUtils.getLocalIp????????????", ex);
	// }
	//
	// BaseBean baseBean = new BaseBean(currDate, creatorId, clientIp, currDate,
	// creatorId, clientIp);
	//
	// for (DeployTaskBean taskBean : tasks) {
	// // ???????????????????????????????????????????????????????????????????????????
	// //deployTaskService.updateTaskStatusCascade(taskBean.getId(),
	// DeployTaskStatus.RUNNING, baseBean);
	//
	//// deployTaskThreadPool.execute(new Runnable() {
	//// public void run() {
	//// processDeployTask(taskBean);
	//// }
	//// });
	// }
	// // if (!deployQueue.isEmpty()) {
	// // String task = deployQueue.poll();
	// // service.execute(new Runnable() {
	// // public void run() {
	// // LOGGER.info(String.format("?????????????????????%s", task));
	// // LOGGER.info(String.format("?????????%s????????????", task));
	// // }
	// // });
	// // }
	// }
	// }
	// }

	// private void processDeployTask(DeployTaskBean taskBean) {
	// try {
	// logManager.add(new DeployLogBean(taskBean.getId(), 0, 0,
	// MessageFormat.format("?????????????????????{0}", JSON.toJSONString(taskBean)),
	// webUtils.getCurrentDateStr(), 1,
	// webUtils.getLocalIp()));
	// System.out.println("processDeployTask end");
	// } catch (Exception ex) {
	// LOGGER.error("processDeployTask????????????", ex);
	// }
	// }

	/**
	 * ??????????????????????????????
	 * 
	 * @return
	 */
	private String generateTaskBatchNo(String projectVersion) {
		// return UUID.randomUUID().toString().replaceAll("-", "").substring(0, 6) + "-"
		// + DateUtil.getStringToday2();
		return projectVersion + "-" + DateUtil.getStringToday2();
	}

	/**
	 * ??????????????????
	 * 
	 * @param task
	 */
	@Transactional(propagation = Propagation.REQUIRED, rollbackForClassName = "Exception")
	public String addDeployTask(DeployAppBean app, String description, String projectVersion, DeployConfigBean config,
			BaseBean baseBean, String[] files) {
		int error = 0;
		String msg = "";
		JSONObject jsonResult = new JSONObject();

		int appId = app.getId();
		int configId = config.getId();

		String taskBatchNo = generateTaskBatchNo(projectVersion);
		while (true) {
			// ???????????????????????????
			if (deployTaskManager.isExistByAppIdAndBatchNo(appId, taskBatchNo) == 0) {
				break;
			}
			taskBatchNo = generateTaskBatchNo(projectVersion);
		}

		// ??????????????????t_deploy_task?????????t_deploy_flow?????????????????????
		List<DeployFlowBean> flows = deployFlowManager.getListByConfigId(configId);
		if (null != flows && flows.size() == 0) {
			jsonResult.put(SysConstants.ERROR_STR, 1);
			jsonResult.put(SysConstants.MSG_STR, "????????????????????????");
			return jsonResult.toJSONString();
		}

		List<DeployTaskBean> taskList = new ArrayList<>();
		for (int i = 0; i < flows.size(); i++) {
			DeployTaskBean taskBean = new DeployTaskBean();
			BeanUtils.copyProperties(baseBean, taskBean);

			taskBean.setFlowId(flows.get(i).getId());
			taskBean.setConfigId(configId);
			taskBean.setConfigName(config.getConfigName());
			taskBean.setAppId(appId);
			taskBean.setFlowType(flows.get(i).getFlowType());
			taskBean.setFlowOrder(flows.get(i).getFlowOrder());
			taskBean.setTargetServerOrderType(flows.get(i).getTargetServerOrderType());
			taskBean.setBatchNo(taskBatchNo);
			taskBean.setStatus(i == 0 ? DeployTaskStatus.WAIT_FOR_DEPLOY : DeployTaskStatus.NOT_READY);
			taskBean.setDescription(description);

			taskList.add(taskBean);
		}
		// ?????????????????????????????????????????????
		deployTaskManager.addBatch(taskList);

		List<DeployTaskServerBean> taskServerListTotal = new ArrayList<>();
		for (int i = 0; i < taskList.size(); i++) {
			List<DeployFlowServerBean> flowServers = flowServerManager.getListByFlowId(taskList.get(i).getFlowId());

			List<DeployTaskServerBean> taskServerList = new ArrayList<>();
			for (DeployFlowServerBean deployFlowServerBean : flowServers) {
				DeployTaskServerBean taskServerBean = new DeployTaskServerBean();
				BeanUtils.copyProperties(baseBean, taskServerBean);

				taskServerBean.setDeployTaskId(taskList.get(i).getId());
				taskServerBean.setAppId(appId);
				taskServerBean.setBatchNo(taskBatchNo);
				taskServerBean.setServerOrder(deployFlowServerBean.getServerOrder());
				taskServerBean.setFlowServerId(deployFlowServerBean.getId());
				taskServerBean.setTargetServerId(deployFlowServerBean.getTargetServerId());
				taskServerBean.setDeployDir(deployFlowServerBean.getDeployDir());
				taskServerBean.setStatus(i == 0 ? DeployTaskStatus.WAIT_FOR_DEPLOY : DeployTaskStatus.NOT_READY);

				taskServerList.add(taskServerBean);
			}

			// ???????????????????????????????????????????????????????????????????????????List???????????????????????????0?????????mybatis???????????????????????????
			if (taskServerList.size() > 0) {
				taskServerManager.addBatch(taskServerList);
				taskServerListTotal.addAll(taskServerList);
			}
		}

		for (DeployTaskServerBean deployTaskServerBean : taskServerListTotal) {
			List<DeployFlowServerToDoBean> flowServerToDos = flowServerToDoManager
					.getListByFlowServerId(deployTaskServerBean.getFlowServerId());

			List<DeployTaskServerToDoBean> taskServerToDoList = new ArrayList<>();
			for (DeployFlowServerToDoBean flowServerToDoBean : flowServerToDos) {
				DeployTaskServerToDoBean taskServerToDoBean = new DeployTaskServerToDoBean();
				BeanUtils.copyProperties(baseBean, taskServerToDoBean);

				taskServerToDoBean.setDeployTaskServerId(deployTaskServerBean.getId());
				taskServerToDoBean.setTodoType(flowServerToDoBean.getTodoType());
				taskServerToDoBean.setTodoOrder(flowServerToDoBean.getTodoOrder());
				taskServerToDoBean.setParam1(flowServerToDoBean.getParam1());
				taskServerToDoBean.setParam2(flowServerToDoBean.getParam2());
				taskServerToDoBean.setParam3(flowServerToDoBean.getParam3());
				taskServerToDoBean.setAppId(appId);
				taskServerToDoBean.setBatchNo(taskBatchNo);
				taskServerToDoBean.setStatus(deployTaskServerBean.getStatus());

				taskServerToDoList.add(taskServerToDoBean);
			}

			// ??????????????????????????????????????????????????????List???????????????????????????0?????????mybatis???????????????????????????
			if (taskServerToDoList.size() > 0) {
				taskServerToDoManager.addBatch(taskServerToDoList);
			}
		}

		List<DeployTaskFileBean> fileList = new ArrayList<>();
		for (String file : files) {
			DeployTaskFileBean fileBean = new DeployTaskFileBean();
			BeanUtils.copyProperties(baseBean, fileBean);

			fileBean.setAppId(appId);
			fileBean.setRelativePath(file);
			fileBean.setBatchNo(taskBatchNo);
			try {
				fileBean.setMd5(FileUtil.getFileMd5(app.getFileDir() + file));
			} catch (Exception ex) {
				LOGGER.error("??????????????????????????????", ex);

				jsonResult.put(SysConstants.ERROR_STR, 1);
				jsonResult.put(SysConstants.MSG_STR, "??????????????????????????????");
				return jsonResult.toJSONString();
			}
			fileList.add(fileBean);
		}
		// ??????????????????????????????
		deployTaskFileManager.addBatch(fileList);

		// ????????????????????????????????????????????????????????????
		for (String file : files) {
			String sourceFile = FileUtil.filePathConvert(app.getFileDir()) + file;
			String targetDir = deploySysConfigService.getDeploySysTempDir() + SysConstants.FILE_SEPARATOR
					+ app.getAppName() + SysConstants.FILE_SEPARATOR + taskBatchNo
					+ file.substring(0, file.lastIndexOf("/"));

			try {
				FileUtils.copyFileToDirectory(new File(sourceFile), new File(targetDir));
			} catch (Exception ex) {
				LOGGER.error(
						"DeployService.addDeployTask params:sourceFile: " + sourceFile + ",targetDir: " + targetDir,
						ex);
				try {
					throw ex;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		jsonResult.put(SysConstants.ERROR_STR, error);
		jsonResult.put(SysConstants.MSG_STR, msg);
		return jsonResult.toJSONString();
	}

	public DTreeResponse getDeployAppFiles(String appFileDir) {
		DTreeResponse resp = new DTreeResponse();
		File rootDir = new File(appFileDir);

		if (!rootDir.exists()) {
			resp.setStatus(new Status(201, "????????????????????????????????????"));
			return resp;
		}
		if (!rootDir.isDirectory()) {
			resp.setStatus(new Status(202, "??????????????????????????????????????????"));
			return resp;
		}

		List<DTree> appFilesTreeData = getAppFilesTreeData(appFileDir, rootDir, null);
		resp.setData(appFilesTreeData);

		return resp;
	}

	private List<DTree> getAppFilesTreeData(String appFileDir, File rootDir, String parentId) {
		List<DTree> appFilesTreeData = new ArrayList<DTree>();
		File[] fileList = rootDir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				if (file.isFile()) {
					if (FileUtil.getTotalFilesInDirExcludeBySuffix(file,
							deploySysConfigService.getFilesTreeIgnore()) > 0) {
						return true;
					}
					return false;
				}
				return true;
			}
		});

		// ?????????????????????????????????
		List<CheckArr> checkArrs0 = new ArrayList<CheckArr>() {
			private static final long serialVersionUID = 1L;

			{
				add(new CheckArr("0", "0"));
			}
		};

		String pid = null != parentId ? parentId : "0";

		for (File file : fileList) {
			if (file.isDirectory()) {
				if (FileUtil.getTotalFilesInDirExcludeBySuffix(file, deploySysConfigService.getFilesTreeIgnore()) > 0) {
					DTree root = new DTree();
					root.setId(FileUtil.filePathConvert(file.getAbsolutePath()).substring(appFileDir.length()));
					root.setTitle(file.getName());
					// root.setLevel("1");//??????level?????????????????????
					root.setSpread(false);
					root.setParentId(pid);
					root.setIsLast(false);
					root.setCheckArr(checkArrs0);
					root.setChildren(getAppFilesTreeData(appFileDir, file, root.getId()));
					appFilesTreeData.add(root);
				}
			} else {
				DTree root = new DTree();
				root.setId(FileUtil.filePathConvert(file.getAbsolutePath()).substring(appFileDir.length()));
				root.setTitle(file.getName());
				// root.setLevel("1");//??????level?????????????????????
				root.setSpread(false);
				root.setParentId(pid);
				root.setIsLast(true);
				root.setCheckArr(checkArrs0);
				appFilesTreeData.add(root);
			}

		}

		return appFilesTreeData;
	}

}
