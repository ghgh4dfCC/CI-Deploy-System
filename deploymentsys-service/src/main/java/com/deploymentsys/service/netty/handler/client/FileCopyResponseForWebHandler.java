package com.deploymentsys.service.netty.handler.client;

import java.net.UnknownHostException;
import java.text.MessageFormat;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.deploymentsys.beans.constants.DeployTaskStatus;
import com.deploymentsys.beans.constants.SysConstants;
import com.deploymentsys.beans.deploy.DeployLogBean;
import com.deploymentsys.beans.deploy.DeployTaskServerToDoBean;
import com.deploymentsys.service.deploy.DeployTaskService;
import com.deploymentsys.tcp.protocol.request.FileCopyRequestPacket;
import com.deploymentsys.tcp.protocol.response.FileCopyResponsePacket;
import com.deploymentsys.utils.WebUtils;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class FileCopyResponseForWebHandler extends SimpleChannelInboundHandler<FileCopyResponsePacket> {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileCopyResponseForWebHandler.class);

	private static final DeployTaskService deployTaskService = new DeployTaskService();
	private static final WebUtils webUtils = new WebUtils();

	private DeployTaskServerToDoBean toDo;

	private String logMsg;

	public FileCopyResponseForWebHandler() {
	}

	public FileCopyResponseForWebHandler(DeployTaskServerToDoBean toDo) {
		this.toDo = toDo;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) {
		// super.channelActive(ctx);
		try {
			FileCopyRequestPacket request = new FileCopyRequestPacket();

			request.setSourceFilePath(toDo.getParam1().replace(SysConstants.BATCHNO_STR, toDo.getBatchNo()));
			request.setTargetDir(toDo.getParam2());

			ctx.channel().writeAndFlush(request);
		} catch (Exception ex) {
			LOGGER.error("FileCopyResponseForWebHandler.channelActive", ex);

			try {
				deployTaskService.addToLogQueue(new DeployLogBean(toDo.getTaskId(), toDo.getDeployTaskServerId(),
						toDo.getId(), ExceptionUtils.getStackTrace(ex), webUtils.getCurrentDateStr(), 1,
						webUtils.getLocalIp()));
			} catch (UnknownHostException e) {
				// e.printStackTrace();
				LOGGER.error("webUtils.getLocalIp????????????", e);
			}

			toDo.setStatus(DeployTaskStatus.FAILURE);
			deployTaskService.addToTaskServerToDoQueue(toDo);
		}
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FileCopyResponsePacket fileCopyResponsePacket) {
		try {
			if (fileCopyResponsePacket.getResponseCode() != 0) {
				logMsg = MessageFormat.format("?????????????????????????????? {0}???????????????????????????{1}", JSON.toJSONString(toDo),
						fileCopyResponsePacket.getResponseMsg());
				// ?????????????????????????????????
				toDo.setStatus(DeployTaskStatus.FAILURE);
			} else {
				boolean done = fileCopyResponsePacket.isDone();

				logMsg = MessageFormat.format("????????????????????????{0} {1}, responseMsg: {2}", done ? "??????" : "??????",
						JSON.toJSONString(toDo), fileCopyResponsePacket.getResponseMsg());

				toDo.setStatus(done ? DeployTaskStatus.SUCCESS : DeployTaskStatus.FAILURE);
			}

			LOGGER.info(logMsg);
			deployTaskService.addToTaskServerToDoQueue(toDo);

			deployTaskService.addToLogQueue(new DeployLogBean(toDo.getTaskId(), toDo.getDeployTaskServerId(),
					toDo.getId(), logMsg, webUtils.getCurrentDateStr(), 1, webUtils.getLocalIp()));

			closeActions(ctx);

		} catch (Exception ex) {
			LOGGER.error("FileCopyResponseForWebHandler.channelRead0 ?????????????????????", ex);
			// ?????????????????????????????????
			toDo.setStatus(DeployTaskStatus.FAILURE);
			deployTaskService.addToTaskServerToDoQueue(toDo);

			try {
				deployTaskService.addToLogQueue(new DeployLogBean(toDo.getTaskId(), toDo.getDeployTaskServerId(),
						toDo.getId(), ExceptionUtils.getStackTrace(ex), webUtils.getCurrentDateStr(), 1,
						webUtils.getLocalIp()));
			} catch (UnknownHostException e1) {
				LOGGER.error("webUtils.getLocalIp() ", e1);
			}

			try {
				closeActions(ctx);
			} catch (InterruptedException e) {
				LOGGER.error("FileCopyResponseForWebHandler.closeActions ?????????????????????", ex);
			}
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		LOGGER.error("FileCopyResponseForWebHandler.exceptionCaught???????????????:", cause);
		// LOGGER.info("???????????????????????????????????????");
		// super.exceptionCaught(ctx, cause);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		LOGGER.info("FileCopyResponseForWebHandler.channelInactive: ??????????????????????????????");
		// ??????????????????????????????
		// LOGGER.info("?????? {} ???????????????", fileName);
		// LOGGER.info("???????????????????????????????????????");

		// super.channelInactive(ctx);
	}

	private void closeActions(ChannelHandlerContext ctx) throws InterruptedException {
		LOGGER.info("??????ChannelHandlerContext???Channel?????????");
		ctx.channel().close().sync();
		ctx.close().sync();
	}

}
