package com.example.zhongxin.service.impl;


import com.example.zhongxin.dao.*;
import com.example.zhongxin.enums.MsgActionEnum;
import com.example.zhongxin.enums.MsgSignFlagEnum;
import com.example.zhongxin.enums.SearchFriendsStatusEnum;
import com.example.zhongxin.idworker.Sid;
import com.example.zhongxin.netty.ChatMsg;
import com.example.zhongxin.netty.DataContent;
import com.example.zhongxin.netty.UserChannelRel;
import com.example.zhongxin.pojo.FriendsRequest;
import com.example.zhongxin.pojo.MyFriends;
import com.example.zhongxin.pojo.Users;
import com.example.zhongxin.pojo.vo.FriendRequestVO;
import com.example.zhongxin.pojo.vo.MyFriendsVO;
import com.example.zhongxin.service.UserService;
import com.example.zhongxin.utils.FastDFSClient;
import com.example.zhongxin.utils.FileUtils;
import com.example.zhongxin.utils.JsonUtils;
import com.example.zhongxin.utils.QRCodeUtils;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tk.mybatis.mapper.entity.Example;
import tk.mybatis.mapper.entity.Example.Criteria;

import java.io.IOException;
import java.util.Date;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {

	@Autowired
	private UsersMapper userMapper;

	@Autowired
	private UsersMapperCustom usersMapperCustom;

	@Autowired
	private MyFriendsMapper myFriendsMapper;

	@Autowired
	private FriendsRequestMapper friendsRequestMapper;

	@Autowired
	private ChatMsgMapper chatMsgMapper;

	@Autowired
	private Sid sid;

	@Autowired
	private QRCodeUtils qrCodeUtils;

	@Autowired
	private FastDFSClient fastDFSClient;

	@Transactional(propagation = Propagation.SUPPORTS)
	@Override
	public boolean queryUsernameIsExist(String username) {

		Users user = new Users();
		user.setUsername(username);

		Users result = userMapper.selectOne(user);

		return result != null ? true : false;
	}

	@Transactional(propagation = Propagation.SUPPORTS)
	@Override
	public Users queryUserForLogin(String username, String pwd) {

		Example userExample = new Example(Users.class);
		Criteria criteria = userExample.createCriteria();

		criteria.andEqualTo("username", username);
		criteria.andEqualTo("password", pwd);

		Users result = userMapper.selectOneByExample(userExample);

		return result;
	}

	@Transactional(propagation = Propagation.REQUIRED)
	@Override
	public Users saveUser(Users user) {

		String userId = sid.nextShort();

		// 为每个用户生成一个唯一的二维码
		//windows下的路径
//		String qrCodePath = "E:\\picture\\qrcode\\user" + userId + "qrcode.png";
		//linux下的路径
		String qrCodePath = "//home//ubuntu//picture//qrcode//user" + userId + "qrcode.png";
		// muxin_qrcode:[username]
		qrCodeUtils.createQRCode(qrCodePath, "muxin_qrcode:" + user.getUsername());
		MultipartFile qrCodeFile = FileUtils.fileToMultipart(qrCodePath);

		String qrCodeUrl = "";
		try {
			qrCodeUrl = fastDFSClient.uploadQRCode(qrCodeFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
		user.setQrcode(qrCodeUrl);

		user.setId(userId);
		userMapper.insert(user);

		return user;
	}

	@Transactional(propagation = Propagation.REQUIRED)
	@Override
	public Users updateUserInfo(Users user) {
		userMapper.updateByPrimaryKeySelective(user);
		return queryUserById(user.getId());
	}

	@Transactional(propagation = Propagation.SUPPORTS)
	public Users queryUserById(String userId) {
		return userMapper.selectByPrimaryKey(userId);
	}

	@Transactional(propagation = Propagation.SUPPORTS)
	@Override
	public Integer preconditionSearchFriends(String myUserId, String friendUsername) {

		Users user = queryUserInfoByUsername(friendUsername);

		// 1. 搜索的用户如果不存在，返回[无此用户]
		if (user == null) {
			return SearchFriendsStatusEnum.USER_NOT_EXIST.status;
		}

		// 2. 搜索账号是你自己，返回[不能添加自己]
		if (user.getId().equals(myUserId)) {
			return SearchFriendsStatusEnum.NOT_YOURSELF.status;
		}

		// 3. 搜索的朋友已经是你的好友，返回[该用户已经是你的好友]
		Example mfe = new Example(MyFriends.class);
		Criteria mfc = mfe.createCriteria();
		mfc.andEqualTo("myUserId", myUserId);
		mfc.andEqualTo("myFriendUserId", user.getId());
		MyFriends myFriendsRel = myFriendsMapper.selectOneByExample(mfe);
		if (myFriendsRel != null) {
			return SearchFriendsStatusEnum.ALREADY_FRIENDS.status;
		}

		return SearchFriendsStatusEnum.SUCCESS.status;
	}

	@Transactional(propagation = Propagation.SUPPORTS)
	@Override
	public Users queryUserInfoByUsername(String username) {
		Example ue = new Example(Users.class);
		Criteria uc = ue.createCriteria();
		uc.andEqualTo("username", username);
		return userMapper.selectOneByExample(ue);
	}

	@Transactional(propagation = Propagation.REQUIRED)
	@Override
	public void sendFriendRequest(String myUserId, String friendUsername) {

		// 根据用户名把朋友信息查询出来
		Users friend = queryUserInfoByUsername(friendUsername);

		// 1. 查询发送好友请求记录表
		Example fre = new Example(FriendsRequest.class);
		Criteria frc = fre.createCriteria();
		frc.andEqualTo("sendUserId", myUserId);
		frc.andEqualTo("acceptUserId", friend.getId());
		FriendsRequest friendRequest = friendsRequestMapper.selectOneByExample(fre);
		if (friendRequest == null) {
			// 2. 如果不是你的好友，并且好友记录没有添加，则新增好友请求记录
			String requestId = sid.nextShort();

			FriendsRequest request = new FriendsRequest();
			request.setId(requestId);
			request.setSendUserId(myUserId);
			request.setAcceptUserId(friend.getId());
			request.setRequestDateTime(new Date());
			friendsRequestMapper.insert(request);
		}
	}

	@Transactional(propagation = Propagation.SUPPORTS)
	@Override
	public List<FriendRequestVO> queryFriendRequestList(String acceptUserId) {
		return usersMapperCustom.queryFriendRequestList(acceptUserId);
	}

	@Transactional(propagation = Propagation.REQUIRED)
	@Override
	public void deleteFriendRequest(String sendUserId, String acceptUserId) {
		Example fre = new Example(FriendsRequest.class);
		Criteria frc = fre.createCriteria();
		frc.andEqualTo("sendUserId", sendUserId);
		frc.andEqualTo("acceptUserId", acceptUserId);
		friendsRequestMapper.deleteByExample(fre);
	}

	@Transactional(propagation = Propagation.REQUIRED)
	@Override
	public void passFriendRequest(String sendUserId, String acceptUserId) {
		saveFriends(sendUserId, acceptUserId);
		saveFriends(acceptUserId, sendUserId);
		deleteFriendRequest(sendUserId, acceptUserId);

		Channel sendChannel = UserChannelRel.get(sendUserId);
		if (sendChannel != null) {
			// 使用websocket主动推送消息到请求发起者，更新他的通讯录列表为最新
			DataContent dataContent = new DataContent();
			dataContent.setAction(MsgActionEnum.PULL_FRIEND.type);

			sendChannel.writeAndFlush(
					new TextWebSocketFrame(
							JsonUtils.objectToJson(dataContent)));
		}
	}

	@Transactional(propagation = Propagation.REQUIRED)
	private void saveFriends(String sendUserId, String acceptUserId) {
		MyFriends myFriends = new MyFriends();
		String recordId = sid.nextShort();
		myFriends.setId(recordId);
		myFriends.setMyFriendUserId(acceptUserId);
		myFriends.setMyUserId(sendUserId);
		myFriendsMapper.insert(myFriends);
	}

	@Transactional(propagation = Propagation.SUPPORTS)
	@Override
	public List<MyFriendsVO> queryMyFriends(String userId) {
		List<MyFriendsVO> myFirends = usersMapperCustom.queryMyFriends(userId);
		return myFirends;
	}

	@Transactional(propagation = Propagation.REQUIRED)
	@Override
	public String saveMsg(ChatMsg chatMsg) {

		com.example.zhongxin.pojo.ChatMsg msgDB = new com.example.zhongxin.pojo.ChatMsg();
		String msgId = sid.nextShort();
		msgDB.setId(msgId);
		msgDB.setAcceptUserId(chatMsg.getReceiverId());
		msgDB.setSendUserId(chatMsg.getSenderId());
		msgDB.setCreateTime(new Date());
		msgDB.setSignFlag(MsgSignFlagEnum.unsign.type);
		msgDB.setMsg(chatMsg.getMsg());

		chatMsgMapper.insert(msgDB);

		return msgId;

	}

	@Transactional(propagation = Propagation.REQUIRED)
	@Override
	public void updateMsgSigned(List<String> msgIdList) {
		usersMapperCustom.batchUpdateMsgSigned(msgIdList);
	}

	@Transactional(propagation = Propagation.SUPPORTS)
	@Override
	public List<com.example.zhongxin.pojo.ChatMsg> getUnReadMsgList(String acceptUserId) {

		Example chatExample = new Example(com.example.zhongxin.pojo.ChatMsg.class);
		Criteria chatCriteria = chatExample.createCriteria();
		chatCriteria.andEqualTo("signFlag", 0);
		chatCriteria.andEqualTo("acceptUserId", acceptUserId);

		List<com.example.zhongxin.pojo.ChatMsg> result = chatMsgMapper.selectByExample(chatExample);

		return result;
	}
}