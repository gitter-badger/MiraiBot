import net.mamoe.mirai.Mirai;
import net.mamoe.mirai.contact.Member;
import net.mamoe.mirai.contact.MemberPermission;
import net.mamoe.mirai.event.EventHandler;
import net.mamoe.mirai.event.ListenerHost;
import net.mamoe.mirai.event.events.*;
import net.mamoe.mirai.message.code.MiraiCode;
import net.mamoe.mirai.message.data.At;
import net.mamoe.mirai.message.data.MessageChain;
import net.mamoe.mirai.message.data.MessageSource;
import net.mamoe.mirai.message.data.QuoteReply;

import java.io.File;

public class EventListener implements ListenerHost {
	public static final MessageSource[] messages = new MessageSource[1024];
	public static final int[] messageI = {0};
	public static boolean showQQ;
	public static File autoRespond;
	
	@EventHandler
	public void onInvited(BotInvitedJoinGroupRequestEvent event){
		if (ConfigUtil.getConfig("inviteAccept").equals("true")){
			event.accept();
			LogUtil.log("机器人已接受 " + event.getInvitorNick() + showQQ(event.getInvitorId()) +
					" 的邀请，加入了聊群 " + event.getGroupName() + "(" + event.getGroupId() + ")");
		}
	}
	@EventHandler
	public void onNewFriend(NewFriendRequestEvent event){
		if (ConfigUtil.getConfig("inviteAccept").equals("true")) {
			event.accept();
			LogUtil.log("机器人已接受 " + event.getFromNick() + showQQ(event.getFromId()) + "的好友申请，");
			LogUtil.log("其添加好友的信息为：" + event.getMessage());
		}
	}
	@EventHandler
	public void onGroupRecall(MessageRecallEvent.GroupRecall event){
		if (event.getGroup().getId() != Long.parseLong(ConfigUtil.getConfig("group"))) {
			return;
		}
		Member operator = event.getOperator();
		Member sender = event.getAuthor();
		int id = -1;
		for (int i = 0; i < messageI[0]; i++) {
			if (messages[i].getTime() == event.getMessageTime()) {
				id = i + 1;
			}
		}
		if (operator != null) {
			if (operator.getId() == sender.getId()){
				if (id != -1) {
					LogUtil.log(operator.getNameCard() + showQQ(operator.getId()) + "撤回了 [" + id + "] 消息");
				} else {
					LogUtil.log(operator.getNameCard() + showQQ(operator.getId()) + "撤回了一条消息");
				}
			} else {
				if (id != -1) {
					LogUtil.log(operator.getNameCard() + showQQ(operator.getId()) + "撤回了一条 " +
							sender.getNameCard() + showQQ(sender.getId()) + "的 [" + id + "] 消息");
				} else {
					LogUtil.log(operator.getNameCard() + showQQ(operator.getId()) + "撤回了一条 " +
						sender.getNameCard() + showQQ(sender.getId()) + "的消息");
				}
			}
		} else {
			if (id != -1) {
				LogUtil.log(event.getBot().getNick() + showQQ(event.getBot().getId()) + "撤回了 [" + id + "] 消息");
			} else {
				LogUtil.log(event.getBot().getNick() + showQQ(event.getBot().getId()) + "撤回了一条消息");
			}
		}
	}
	@EventHandler
	public void onFriendRecall(MessageRecallEvent.FriendRecall event){
		if (!(ConfigUtil.getConfig("friend").equals("*") ||
				event.getOperator().getId() == Long.parseLong(ConfigUtil.getConfig("friend")))) {
			return;
		}
		int id = -1;
		for (int i = 0; i < messageI[0]; i++) {
			if (messages[i].getTime() == event.getMessageTime()) {
				id = i + 1;
			}
		}
		if (id != -1) {
			LogUtil.log(event.getOperator().getNick() + showQQ(event.getOperator().getId()) + "撤回了 [" + id + "] 消息");
		} else {
			LogUtil.log(event.getOperator().getNick() + showQQ(event.getOperator().getId()) + "撤回了一条消息");
		}
	}
	@EventHandler
	public void onGroupPostSend(GroupMessagePostSendEvent event) {
		LogUtil.log(event.getBot().getNick() + " : " +
				(ConfigUtil.getConfig("debug").equals("true") ?
						event.getMessage().serializeToMiraiCode() : event.getMessage().contentToString()));
	}
	@EventHandler
	public void onFriendPostSend(FriendMessagePostSendEvent event) {
		LogUtil.log(event.getBot().getNick() + " -> " + event.getTarget().getNick() + showQQ(event.getTarget().getId()) +
				(ConfigUtil.getConfig("debug").equals("true") ?
					event.getMessage().serializeToMiraiCode() : event.getMessage().contentToString()));
	}
	@EventHandler
	public void onImageUpload(BeforeImageUploadEvent event){
		LogUtil.log("正在上传图片...");
	}
	@EventHandler
	public void onGroupMessage(GroupMessageEvent event) {
		if (event.getGroup().getId() != Long.parseLong(ConfigUtil.getConfig("group"))) {
			return;
		}
		String mCode = event.getMessage().serializeToMiraiCode();
		String msg = ConfigUtil.getConfig("debug").equals("true") ?
				event.getMessage().serializeToMiraiCode() : event.getMessage().contentToString();
		
		messages[messageI[0]] = event.getSource();
		messageI[0]++;
		if (messageI[0] == 1024){
			messageI[0] = 0;
		}
		LogUtil.log("[" + messageI[0] + "] " + event.getSender().getNameCard() + showQQ(event.getSender().getId()) + ": " + msg);
		
		for (String section : IniUtil.getSectionNames(autoRespond)) {
			if (!section.isEmpty()) {
				String regex = IniUtil.getValue(section, "Message", autoRespond);
				String respond = IniUtil.getValue(section, "Respond", autoRespond);
				if (respond != null && regex != null) {
					respond = replacePlaceholder(event, respond);
					regex = replacePlaceholder(event, regex);
					if (mCode.matches(regex)) {
						if (respond.startsWith("[reply]")) {
							event.getGroup().sendMessage(new QuoteReply(event.getSource()).plus(
									MiraiCode.deserializeMiraiCode(respond.substring(7))));
						} else {
							event.getGroup().sendMessage(MiraiCode.deserializeMiraiCode(respond));
						}
					}
				}
			}
		}
		
		if (mCode.split(":").length >= 3 && mCode.split(":")[1].equals("flash")){
			if (ConfigUtil.getConfig("autoFlash").equals("true")){
				if (event.getSender().getPermission() != MemberPermission.OWNER &&
						event.getGroup().getBotPermission() != MemberPermission.MEMBER) {
					Mirai.getInstance().recallMessage(event.getBot(), event.getSource());
				}
				MessageChain send = MiraiCode.deserializeMiraiCode(mCode.replace("flash", "image"));
				event.getGroup().sendMessage(send);
			}
		}
		
		if (mCode.startsWith("[mirai:at:" + event.getBot().getId() + "] ")){
			String[] cmd = mCode.split(" ");
			if (cmd[1].equals("帮助") || cmd[1].equals("help")) {
				String help =
						"· --------====== MiraiBot ======-------- ·\n" +
						"1. 禁言 <秒>\n" +
						"- 禁言自己一段时间\n" +
						"· -------------------------------------- ·\n";
				event.getGroup().sendMessage(help);
			} else if (cmd[1].equals("禁言") && cmd.length > 2){
				try {
					if (event.getSender().getPermission() != MemberPermission.OWNER &&
							event.getGroup().getBotPermission() != MemberPermission.MEMBER){
						if (Integer.parseInt(cmd[2]) > 0 && Integer.parseInt(cmd[2]) < 2592000){
							event.getSender().mute(Integer.parseInt(cmd[2]));
							event.getGroup().sendMessage(new QuoteReply(event.getSource()).plus(
									new At(event.getSender().getId()).plus("头一次听说这么奇怪的要求...")));
						} else {
							event.getGroup().sendMessage(new QuoteReply(event.getSource()).plus(
									new At(event.getSender().getId()).plus("这数字不河里啊...!")));
						}
					} else {
						event.getGroup().sendMessage(new QuoteReply(event.getSource()).plus(
								new At(event.getSender().getId()).plus("俺 莫 得 权 限")));
					}
				} catch (NumberFormatException e) {
					event.getGroup().sendMessage(new QuoteReply(event.getSource()).plus(
							new At(event.getSender().getId()).plus("\"" + cmd[2] + "\" 是多少秒...")));
				}
			}
		}
	}
	@EventHandler
	public void onFriendMessage(FriendMessageEvent event){
		if (!(ConfigUtil.getConfig("friend").equals("*") ||
				event.getSender().getId() == Long.parseLong(ConfigUtil.getConfig("friend")))) {
			return;
		}
		String msg = ConfigUtil.getConfig("debug").equals("true") ?
				event.getMessage().plus("").serializeToMiraiCode() : event.getMessage().contentToString();
		LogUtil.log(event.getSender().getNick() + showQQ(event.getSender().getId()) + "-> " + event.getBot().getNick() + " " + msg);
	}
	@EventHandler
	public void onTempMessage(GroupTempMessageEvent event){
		if (!(ConfigUtil.getConfig("friend").equals("*") ||
				event.getSender().getId() == Long.parseLong(ConfigUtil.getConfig("friend")))){
			return;
		}
		String msg = ConfigUtil.getConfig("debug").equals("true") ?
				event.getMessage().plus("").serializeToMiraiCode() : event.getMessage().contentToString();
		LogUtil.log(event.getSender().getNick() + showQQ(event.getSender().getId()) + "-> " + event.getBot().getNick() + " " + msg);
	}
	@EventHandler
	public void onStrangerMessage(StrangerMessageEvent event){
		if (!(ConfigUtil.getConfig("friend").equals("*") ||
				event.getSender().getId() == Long.parseLong(ConfigUtil.getConfig("friend")))){
			return;
		}
		String msg = ConfigUtil.getConfig("debug").equals("true") ?
				event.getMessage().plus("").serializeToMiraiCode() : event.getMessage().contentToString();
		LogUtil.log(event.getSender().getNick() + showQQ(event.getSender().getId()) + "-> " + event.getBot().getNick() + " " + msg);
	}
	
	public String showQQ(Long qq){
		if (showQQ) {
			return "(" + qq + ") ";
		}
		return " ";
	}
	
	public String replacePlaceholder(GroupMessageEvent event, String str) {
		str = str.replaceAll("%sender_nick%", event.getSender().getNick());
		str = str.replaceAll("%sender_id%", String.valueOf(event.getSender().getId()));
		str = str.replaceAll("%sender_nameCard%", event.getSender().getNameCard());
		str = str.replaceAll("%group_name%", event.getGroup().getName());
		str = str.replaceAll("%group_id%", String.valueOf(event.getSender().getId()));
		str = str.replaceAll("%group_owner_nick%", event.getGroup().getOwner().getNick());
		str = str.replaceAll("%group_owner_id%", String.valueOf(event.getGroup().getOwner().getId()));
		str = str.replaceAll("%group_owner_nameCard%", event.getGroup().getOwner().getNameCard());
		str = str.replaceAll("%message_miraiCode%", event.getMessage().serializeToMiraiCode());
		str = str.replaceAll("%message_content%", event.getMessage().contentToString());
		str = str.replaceAll("%bot_nick%", event.getBot().getNick());
		str = str.replaceAll("%bot_id%", String.valueOf(event.getBot().getId()));
		return str;
	}
}
