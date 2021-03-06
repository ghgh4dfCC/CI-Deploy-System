package com.deploymentsys.service.netty.handler.client;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.UnknownHostException;
import java.text.MessageFormat;
import java.util.List;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;

import com.deploymentsys.beans.constants.DeployTaskStatus;
import com.deploymentsys.beans.deploy.DeployLogBean;
import com.deploymentsys.beans.deploy.DeployTaskFileBean;
import com.deploymentsys.beans.deploy.DeployTaskServerToDoBean;
import com.deploymentsys.service.deploy.DeployTaskService;
import com.deploymentsys.tcp.protocol.request.FileTransferRequestPacket;
import com.deploymentsys.tcp.protocol.response.FileTransferResponsePacket;
import com.deploymentsys.utils.FileUtil;
import com.deploymentsys.utils.WebUtils;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class FileTransferResponseForWebHandler extends SimpleChannelInboundHandler<FileTransferResponsePacket> {
	private static final Logger LOGGER = LoggerFactory.getLogger(FileTransferResponseForWebHandler.class);

	private static final DeployTaskService deployTaskService = new DeployTaskService();
	private static final WebUtils webUtils = new WebUtils();

	// private NioEventLoopGroup workerGroup;

	private DeployTaskServerToDoBean toDo;
	private String sourceFilePathBase;
	private String targetDirBase;
	private List<DeployTaskFileBean> files;

	private String sourceFilePathCurrent = "";
	private String targetDirCurrent = "";
	private String relativePathCurrent = "";

	private String logMsg;

	private int currentFileIndex = 0;
	private DeployTaskFileBean currentTaskFile;

	private boolean isNewFile = false;

	public FileTransferResponseForWebHandler() {
	}

	public FileTransferResponseForWebHandler(List<DeployTaskFileBean> files, String sourceFilePathBase,
			String targetDirBase, DeployTaskServerToDoBean toDo) {
		this.sourceFilePathBase = sourceFilePathBase;
		this.targetDirBase = targetDirBase;
		this.toDo = toDo;
		this.files = files;
	}

	private void fileStartTransfer(DeployTaskFileBean currentFile, ChannelHandlerContext ctx)
			throws InterruptedException, FileNotFoundException, IOException {
		isNewFile = false;
		relativePathCurrent = currentFile.getRelativePath();
		sourceFilePathCurrent = sourceFilePathBase + relativePathCurrent;

		File tempFile = new File(sourceFilePathCurrent);
		if (!tempFile.exists() || !tempFile.isFile()) {
			logMsg = MessageFormat.format("?????????{0} ????????????????????????", sourceFilePathCurrent);

			LOGGER.info(logMsg);
			deployTaskService.addToLogQueue(new DeployLogBean(toDo.getTaskId(), toDo.getDeployTaskServerId(),
					toDo.getId(), logMsg, webUtils.getCurrentDateStr(), 1, webUtils.getLocalIp()));

			toDo.setStatus(DeployTaskStatus.FAILURE);
			deployTaskService.addToTaskServerToDoQueue(toDo);

			closeActions(ctx);
			return;
		}

		// ??????????????????????????????MD5
		String md5Current = FileUtil.getFileMd5(sourceFilePathCurrent);
		if (!md5Current.equals(currentFile.getMd5())) {
			logMsg = MessageFormat.format("?????????{0} md5???????????????", sourceFilePathCurrent);

			LOGGER.info(logMsg);
			deployTaskService.addToLogQueue(new DeployLogBean(toDo.getTaskId(), toDo.getDeployTaskServerId(),
					toDo.getId(), logMsg, webUtils.getCurrentDateStr(), 1, webUtils.getLocalIp()));

			toDo.setStatus(DeployTaskStatus.FAILURE);
			deployTaskService.addToTaskServerToDoQueue(toDo);

			closeActions(ctx);
			return;
		}

		targetDirCurrent = targetDirBase + relativePathCurrent.substring(0, relativePathCurrent.lastIndexOf("/"));

		LOGGER.info("???????????????????????? {} ", tempFile.getName());
		FileTransferRequestPacket request = new FileTransferRequestPacket();
		request.setSourceFilePath(sourceFilePathCurrent);
		request.setFileName(tempFile.getName());
		request.setTargetDir(targetDirCurrent);

		request.setStartPos(0);

		int perByteLength = 5 * 1024 * 1024;// ??????????????????????????????
		RandomAccessFile randomAccessFile = new RandomAccessFile(sourceFilePathCurrent, "r");
		long fileLength = randomAccessFile.length();
		request.setFileLength(fileLength);

		if (fileLength > 0) {
			int transferByteLength = fileLength > perByteLength ? perByteLength : (int) fileLength;
			byte[] bytes = new byte[transferByteLength];
			randomAccessFile.seek(0);
			randomAccessFile.read(bytes);

			request.setMd5(md5Current);
			request.setBytes(bytes);
		} else {
			request.setBytes(null);
		}

		randomAccessFile.close();
		ctx.channel().writeAndFlush(request);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		// super.channelActive(ctx);
		try {
			currentTaskFile = files.get(currentFileIndex);
			fileStartTransfer(currentTaskFile, ctx);

		} catch (Exception ex) {
			LOGGER.error("FileTransferResponseForWebHandler.channelActive", ex);

			try {
				deployTaskService.addToLogQueue(new DeployLogBean(toDo.getTaskId(), toDo.getDeployTaskServerId(),
						toDo.getId(), ExceptionUtils.getStackTrace(ex), webUtils.getCurrentDateStr(), 1,
						webUtils.getLocalIp()));
			} catch (UnknownHostException e) {
				LOGGER.error("webUtils.getLocalIp????????????", e);
			}

			toDo.setStatus(DeployTaskStatus.FAILURE);
			deployTaskService.addToTaskServerToDoQueue(toDo);
		}
	}

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, FileTransferResponsePacket fileTransferResponsePacket) {
		String fileName = fileTransferResponsePacket.getFileName();// ?????????

		try {
			if (fileTransferResponsePacket.getResponseCode() != 0) {
				logMsg = MessageFormat.format("?????? {0} ???????????????????????????????????????{1}", fileName,
						fileTransferResponsePacket.getResponseMsg());
				LOGGER.info(logMsg);
				// ?????????????????????????????????
				toDo.setStatus(DeployTaskStatus.FAILURE);
				deployTaskService.addToTaskServerToDoQueue(toDo);

				deployTaskService.addToLogQueue(new DeployLogBean(toDo.getTaskId(), toDo.getDeployTaskServerId(),
						toDo.getId(), logMsg, webUtils.getCurrentDateStr(), 1, webUtils.getLocalIp()));

				closeActions(ctx);
			} else {
				long startPos = fileTransferResponsePacket.getStartPos();
				long lastEndPos = fileTransferResponsePacket.getEndPos();
				long fileLength = fileTransferResponsePacket.getFileLength();

				// ?????????????????????????????????????????????????????????????????????????????????????????????????????????md5????????????
				if (lastEndPos == fileLength) {
					logMsg = MessageFormat.format("?????? {0} ????????????", fileName);
					LOGGER.info(logMsg);
					deployTaskService.addToLogQueue(new DeployLogBean(toDo.getTaskId(), toDo.getDeployTaskServerId(),
							toDo.getId(), logMsg, webUtils.getCurrentDateStr(), 1, webUtils.getLocalIp()));

					boolean md5ValidateResult = false;
					// ????????????????????????
					if (fileLength > 0) {
						md5ValidateResult = fileTransferResponsePacket.isMd5Validate();
						logMsg = MessageFormat.format("?????? {0} md5????????????????????????????????????{1}", fileName,
								md5ValidateResult ? "??????" : "?????????");
						LOGGER.info(logMsg);

						if (!md5ValidateResult) {
							toDo.setStatus(DeployTaskStatus.FAILURE);

							deployTaskService.addToLogQueue(
									new DeployLogBean(toDo.getTaskId(), toDo.getDeployTaskServerId(), toDo.getId(),
											logMsg, webUtils.getCurrentDateStr(), 1, webUtils.getLocalIp()));

							deployTaskService.addToTaskServerToDoQueue(toDo);
							closeActions(ctx);
							return;
						}
						// toDo.setStatus(md5ValidateResult ? DeployTaskStatus.SUCCESS :
						// DeployTaskStatus.FAILURE);
					} else {
						logMsg = MessageFormat.format("?????? {0} md5????????????????????????????????????{1}", fileName, "???????????????0???????????????");
						LOGGER.info(logMsg);

						md5ValidateResult = true;
					}

					deployTaskService.addToLogQueue(new DeployLogBean(toDo.getTaskId(), toDo.getDeployTaskServerId(),
							toDo.getId(), logMsg, webUtils.getCurrentDateStr(), 1, webUtils.getLocalIp()));

					if ((currentFileIndex + 1) == files.size()) {
						toDo.setStatus(md5ValidateResult ? DeployTaskStatus.SUCCESS : DeployTaskStatus.FAILURE);
						deployTaskService.addToTaskServerToDoQueue(toDo);
						closeActions(ctx);

						return;
					} else {
						currentFileIndex++;
						isNewFile = true;
					}
				}

				if (isNewFile) {
					currentTaskFile = files.get(currentFileIndex);
					fileStartTransfer(currentTaskFile, ctx);
				} else {
					LOGGER.info("=========?????? {} ????????? {} ??????============", fileName, lastEndPos);
					RandomAccessFile randomAccessFile = new RandomAccessFile(
							fileTransferResponsePacket.getSourceFilePath(), "r");
					randomAccessFile.seek(startPos);

					long perByteLength = 5 * 1024 * 1024;// ??????????????????????????????
					long leftByteLength = fileLength - startPos;// ????????????????????????
					long transferByteLength = leftByteLength > perByteLength ? perByteLength : leftByteLength;

					FileTransferRequestPacket request = new FileTransferRequestPacket();
					BeanUtils.copyProperties(fileTransferResponsePacket, request);

					byte[] bytes = new byte[(int) transferByteLength];
					randomAccessFile.read(bytes);

					request.setStartPos(startPos);
					request.setBytes(bytes);

					randomAccessFile.close();
					randomAccessFile = null;
					ctx.channel().writeAndFlush(request);
				}

			}

		} catch (Exception ex) {
			LOGGER.error("FileTransferResponseHandler.channelRead0 ?????????????????????", ex);
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
				LOGGER.error("FileTransferResponseHandler.closeActions ?????????????????????", ex);
			}

		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		LOGGER.error("FileTransferResponseHandler.exceptionCaught???????????????:", cause);
		// LOGGER.info("???????????????????????????????????????");
		// super.exceptionCaught(ctx, cause);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		LOGGER.info("FileTransferResponseHandler.channelInactive: ??????????????????????????????");
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
