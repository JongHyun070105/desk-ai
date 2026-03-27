import 'package:flutter/material.dart';
import '../../../core/native_bridge.dart';
import '../../../core/theme/app_theme.dart';

/// 채팅 상세 화면
/// 특정 채팅방의 메시지를 카카오톡 스타일 버블 UI로 표시합니다.
class ChatDetailScreen extends StatefulWidget {
  final String roomId;
  final String displayName;

  const ChatDetailScreen({
    super.key,
    required this.roomId,
    required this.displayName,
  });

  @override
  State<ChatDetailScreen> createState() => _ChatDetailScreenState();
}

class _ChatDetailScreenState extends State<ChatDetailScreen> {
  List<Map<String, dynamic>> _messages = [];
  bool _isLoading = false;
  bool _isLoadingMore = false;
  bool _hasMore = true;
  int _offset = 0;
  final int _limit = 50;

  final ScrollController _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _loadMessages();
    
    // 무한 스크롤 리스너 추가
    _scrollController.addListener(() {
      if (_scrollController.position.pixels >= _scrollController.position.maxScrollExtent - 200 && 
          !_isLoadingMore && _hasMore) {
        _loadMoreMessages();
      }
    });
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  Future<void> _loadMessages() async {
    setState(() {
      _isLoading = true;
      _offset = 0;
      _hasMore = true;
      _messages.clear();
    });
    try {
      final messages = await NativeBridge.getChatMessages(widget.roomId, limit: _limit, offset: _offset);
      setState(() {
        _messages = messages;
        _offset += messages.length;
        _hasMore = messages.length == _limit;
      });
    } finally {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _loadMoreMessages() async {
    if (_isLoadingMore || !_hasMore) return;
    setState(() => _isLoadingMore = true);
    try {
      final messages = await NativeBridge.getChatMessages(widget.roomId, limit: _limit, offset: _offset);
      setState(() {
        _messages.addAll(messages);
        _offset += messages.length;
        _hasMore = messages.length == _limit;
      });
    } finally {
      setState(() => _isLoadingMore = false);
    }
  }

  void _showRuleDialog() async {
    final roomInfo = await NativeBridge.getRoomRule(widget.roomId);
    if (!mounted) return;

    final currentRule = roomInfo?['rule'] as String?;
    bool isAutoReplyEnabled = roomInfo?['isAutoReplyEnabled'] as bool? ?? true;

    final TextEditingController ruleController = TextEditingController(text: currentRule ?? '');
    
    showDialog(
      context: context,
      builder: (context) {
        return StatefulBuilder(
          builder: (context, setDialogState) {
            return AlertDialog(
              title: const Text('채팅방 설정'),
              content: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  SwitchListTile(
                    title: const Text('AI 자동 답장 활성화'),
                    subtitle: const Text('이 채팅방에 AI가 답장을 제안합니다.'),
                    value: isAutoReplyEnabled,
                    activeColor: AppColors.primary,
                    contentPadding: EdgeInsets.zero,
                    onChanged: (val) {
                      setDialogState(() => isAutoReplyEnabled = val);
                    },
                  ),
                  const Divider(),
                  const SizedBox(height: 8),
                  const Align(
                    alignment: Alignment.centerLeft,
                    child: Text('AI 답변 규칙', style: TextStyle(fontWeight: FontWeight.bold)),
                  ),
                  const SizedBox(height: 8),
                  TextField(
                    controller: ruleController,
                    decoration: InputDecoration(
                      hintText: '예: 자연스럽게 대화해줘',
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                      filled: true,
                      fillColor: Theme.of(context).brightness == Brightness.dark 
                          ? Colors.white.withValues(alpha: 0.05) 
                          : Colors.black.withValues(alpha: 0.05),
                    ),
                    maxLines: 3,
                  ),
                ],
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(context),
                  child: const Text('취소'),
                ),
                ElevatedButton(
                  onPressed: () async {
                    await NativeBridge.saveRoomRule(
                      widget.roomId, 
                      ruleController.text.trim(),
                      isAutoReplyEnabled: isAutoReplyEnabled,
                    );
                    if (context.mounted) Navigator.pop(context);
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: AppColors.primary,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                  ),
                  child: const Text('저장'),
                ),
              ],
            );
          }
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            // 작은 아바타
            Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: [AppColors.primary, AppColors.primaryLight],
                ),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Center(
                child: Text(
                  widget.displayName.isNotEmpty
                      ? widget.displayName[0].toUpperCase()
                      : '?',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    widget.displayName,
                    style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  Text(
                    '${_messages.length}개의 메시지',
                    style: TextStyle(
                      fontSize: 11,
                      color: context.textTertiary,
                      fontWeight: FontWeight.w400,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.tune_rounded, size: 22),
            onPressed: _showRuleDialog,
          ),
          IconButton(
            icon: const Icon(Icons.refresh_rounded, size: 22),
            onPressed: _loadMessages,
          ),
        ],
      ),
      body: Column(
        children: [
          // ── 메시지 리스트 ──
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _messages.isEmpty
                    ? Center(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.chat_outlined, size: 48, color: context.textTertiary),
                            const SizedBox(height: 12),
                            Text('메시지가 없습니다', style: TextStyle(color: context.textSecondary)),
                          ],
                        ),
                      )
                    : ListView.builder(
                        controller: _scrollController,
                        reverse: true, // Item 0이 제일 아래(최신 메시지)로 가도록 지원 (DB에서 DESC로 가져옴)
                        padding: EdgeInsets.fromLTRB(12, 16, 12, MediaQuery.of(context).padding.bottom + 100),
                        itemCount: _messages.length + (_isLoadingMore ? 1 : 0),
                        itemBuilder: (context, index) {
                          if (index == _messages.length) {
                             return const Padding(
                               padding: EdgeInsets.all(16.0),
                               child: Center(child: CircularProgressIndicator()),
                             );
                          }
                          // reverse: true 이므로 인덱스가 클수록 과거 메시지
                          final msg = _messages[index];
                          final prevMsg = index < _messages.length - 1 ? _messages[index + 1] : null;
                          return _buildMessageBubble(msg, prevMsg, isDark);
                        },
                      ),
          ),

        ],
      ),
    );
  }

  Widget _buildMessageBubble(
    Map<String, dynamic> msg,
    Map<String, dynamic>? prevMsg,
    bool isDark,
  ) {
    final isSentByMe = msg['isSentByMe'] ?? false;
    final sender = msg['sender']?.toString() ?? '';
    final message = msg['message']?.toString() ?? '';
    final timestamp = msg['timestamp'] as int? ?? 0;

    // 날짜 구분선
    Widget? dateDivider;
    if (prevMsg != null) {
      final prevTime = DateTime.fromMillisecondsSinceEpoch(prevMsg['timestamp'] as int? ?? 0);
      final curTime = DateTime.fromMillisecondsSinceEpoch(timestamp);
      if (prevTime.day != curTime.day || prevTime.month != curTime.month) {
        dateDivider = _buildDateDivider(curTime);
      }
    } else if (timestamp > 0) {
      dateDivider = _buildDateDivider(DateTime.fromMillisecondsSinceEpoch(timestamp));
    }

    // 같은 발신자 연속인지 확인
    final showSender = !isSentByMe &&
        (prevMsg == null || prevMsg['sender'] != sender);

    return Column(
      children: [
        if (dateDivider != null) dateDivider,
        Align(
          alignment: isSentByMe ? Alignment.centerRight : Alignment.centerLeft,
          child: Container(
            margin: EdgeInsets.only(
              top: showSender ? 12 : 3,
              bottom: 3,
              left: isSentByMe ? 60 : 0,
              right: isSentByMe ? 0 : 60,
            ),
            child: Column(
              crossAxisAlignment:
                  isSentByMe ? CrossAxisAlignment.end : CrossAxisAlignment.start,
              children: [
                // 발신자 이름 (수신 메시지만)
                if (showSender)
                  Padding(
                    padding: const EdgeInsets.only(left: 12, bottom: 4),
                    child: Text(
                      sender,
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                        color: context.textSecondary,
                      ),
                    ),
                  ),

                // 버블
                Row(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    if (isSentByMe) ...[
                      Text(
                        _formatTime(timestamp),
                        style: TextStyle(fontSize: 10, color: context.textTertiary),
                      ),
                      const SizedBox(width: 6),
                    ],
                    Flexible(
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
                        decoration: BoxDecoration(
                          color: isSentByMe
                              ? AppColors.primary
                              : (isDark ? AppColors.darkCard : const Color(0xFFF4F1ED)),
                          borderRadius: BorderRadius.only(
                            topLeft: const Radius.circular(18),
                            topRight: const Radius.circular(18),
                            bottomLeft: Radius.circular(isSentByMe ? 18 : 4),
                            bottomRight: Radius.circular(isSentByMe ? 4 : 18),
                          ),
                          border: isSentByMe
                              ? null
                              : Border.all(
                                  color: context.cardBorder.withValues(alpha: 0.5),
                                  width: 0.5,
                                ),
                        ),
                        child: Text(
                          message,
                          style: TextStyle(
                            fontSize: 14,
                            height: 1.4,
                            color: isSentByMe
                                ? Colors.white
                                : context.textPrimary,
                          ),
                        ),
                      ),
                    ),
                    if (!isSentByMe) ...[
                      const SizedBox(width: 6),
                      Text(
                        _formatTime(timestamp),
                        style: TextStyle(fontSize: 10, color: context.textTertiary),
                      ),
                    ],
                  ],
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildDateDivider(DateTime date) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 16),
      child: Row(
        children: [
          Expanded(child: Divider(color: context.dividerColor)),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: Text(
              '${date.year}년 ${date.month}월 ${date.day}일',
              style: TextStyle(
                fontSize: 12,
                color: context.textTertiary,
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          Expanded(child: Divider(color: context.dividerColor)),
        ],
      ),
    );
  }

  String _formatTime(int timestamp) {
    if (timestamp == 0) return '';
    final dt = DateTime.fromMillisecondsSinceEpoch(timestamp);
    final period = dt.hour < 12 ? '오전' : '오후';
    final hour = dt.hour > 12 ? dt.hour - 12 : (dt.hour == 0 ? 12 : dt.hour);
    return '$period $hour:${dt.minute.toString().padLeft(2, '0')}';
  }
}
