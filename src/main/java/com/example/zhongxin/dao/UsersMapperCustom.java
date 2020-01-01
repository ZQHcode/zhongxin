package com.example.zhongxin.dao;

import com.example.zhongxin.pojo.Users;
import com.example.zhongxin.pojo.vo.FriendRequestVO;
import com.example.zhongxin.pojo.vo.MyFriendsVO;
import com.example.zhongxin.utils.MyMapper;

import java.util.List;

public interface UsersMapperCustom extends MyMapper<Users> {
	
	public List<FriendRequestVO> queryFriendRequestList(String acceptUserId);
	
	public List<MyFriendsVO> queryMyFriends(String userId);
	
	public void batchUpdateMsgSigned(List<String> msgIdList);
	
}