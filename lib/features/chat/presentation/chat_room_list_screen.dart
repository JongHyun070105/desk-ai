import 'dart:async';
import 'package:flutter/material.dart';
import '../../../core/native_bridge.dart';
import '../../../core/theme/app_theme.dart';
import '../../../core/widgets/glass_card.dart';
import 'chat_detail_screen.dart';
import '../../fallback/presentation/txt_import_screen.dart';

enum SortOption { date, name }

class ChatRoomListScreen extends StatefulWidget {
  const ChatRoomListScreen({super.key});

  @override
  State<ChatRoomListScreen> createState() => _ChatRoomListScreenState();
}

class _ChatRoomListScreenState extends State<ChatRoomListScreen> {
  List<Map<String, dynamic>> _rooms = [];
  List<Map<String, dynamic>> _filteredRooms = [];
  bool _isLoading = false;
  final _searchController = TextEditingController();
  
  // 신규 추가 상태
  Timer? _refreshTimer;
  SortOption _currentSort = SortOption.date;
  bool _isEditMode = false;
  final Set<String> _selectedRoomIds = {};

  @override
  void initState() {
    super.initState();
    _loadRooms(showLoading: true);
    _searchController.addListener(_filterAndSortRooms);
    
    // 3초마다 자동 새로고침 설정
    _refreshTimer = Timer.periodic(const Duration(seconds: 3), (_) => _loadRooms(showLoading: false));
    
    // 이전 버전 데이터 마이그레이션 실행 (최초 1회)
    NativeBridge.migrateOldRoomIds();
  }

  @override
  void dispose() {
    _refreshTimer?.cancel();
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadRooms({bool showLoading = false}) async {
    if (showLoading) setState(() => _isLoading = true);
    try {
      final rooms = await NativeBridge.getChatRooms();
      if (mounted) {
        setState(() {
          _rooms = rooms;
          _filterAndSortRooms();
        });
      }
    } finally {
      if (showLoading && mounted) setState(() => _isLoading = false);
    }
  }

  void _filterAndSortRooms() {
    final query = _searchController.text.toLowerCase();
    List<Map<String, dynamic>> result = _rooms;

    // 1. 필터링
    if (query.isNotEmpty) {
      result = result.where((room) {
        final roomId = (room['roomId'] ?? '').toString().toLowerCase();
        final lastMessage = (room['lastMessage'] ?? '').toString().toLowerCase();
        return roomId.contains(query) || lastMessage.contains(query);
      }).toList();
    }

    // 2. 정렬
    if (_currentSort == SortOption.date) {
      result.sort((a, b) => (b['lastTimestamp'] as int).compareTo(a['lastTimestamp'] as int));
    } else {
      result.sort((a, b) => (a['roomId'] as String).compareTo(b['roomId'] as String));
    }

    setState(() {
      _filteredRooms = result;
    });
  }

  Future<void> _deleteSelectedRooms() async {
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(_selectedRoomIds.length == _rooms.length ? '전체 삭제' : '선택 삭제'),
        content: Text('${_selectedRoomIds.length}개의 채팅방을 삭제하시겠습니까?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('취소')),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('삭제'),
          ),
        ],
      ),
    );

    if (confirm == true) {
      setState(() => _isLoading = true);
      if (_selectedRoomIds.length == _rooms.length) {
        await NativeBridge.deleteAllMessages();
      } else {
        for (final id in _selectedRoomIds) {
          await NativeBridge.deleteChatRoom(id);
        }
      }
      _selectedRoomIds.clear();
      _isEditMode = false;
      await _loadRooms(showLoading: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      appBar: AppBar(
        title: _isEditMode ? Text('${_selectedRoomIds.length}개 선택됨') : const Text('채팅'),
        leading: _isEditMode
            ? IconButton(
                icon: const Icon(Icons.close_rounded),
                onPressed: () => setState(() {
                  _isEditMode = false;
                  _selectedRoomIds.clear();
                }),
              )
            : null,
        actions: [
          if (!_isEditMode) ...[
            PopupMenuButton<SortOption>(
              icon: const Icon(Icons.sort_rounded),
              onSelected: (option) {
                setState(() => _currentSort = option);
                _filterAndSortRooms();
              },
              itemBuilder: (context) => [
                const PopupMenuItem(value: SortOption.date, child: Text('최신순')),
                const PopupMenuItem(value: SortOption.name, child: Text('이름순')),
              ],
            ),
            IconButton(
              icon: const Icon(Icons.edit_note_rounded),
              onPressed: () => setState(() => _isEditMode = true),
            ),
          ] else ...[
            IconButton(
              icon: Icon(_selectedRoomIds.length == _filteredRooms.length
                  ? Icons.deselect_rounded
                  : Icons.select_all_rounded),
              onPressed: () {
                setState(() {
                  if (_selectedRoomIds.length == _filteredRooms.length) {
                    _selectedRoomIds.clear();
                  } else {
                    _selectedRoomIds.addAll(_filteredRooms.map((r) => r['roomId'] as String));
                  }
                });
              },
            ),
            IconButton(
              icon: const Icon(Icons.delete_sweep_rounded, color: Colors.redAccent),
              onPressed: _selectedRoomIds.isEmpty ? null : _deleteSelectedRooms,
            ),
          ],
        ],
      ),
      body: Column(
        children: [
          if (!_isEditMode)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
              child: TextField(
                controller: _searchController,
                decoration: InputDecoration(
                  hintText: '채팅방 검색...',
                  prefixIcon: Icon(Icons.search_rounded, color: context.textTertiary),
                  suffixIcon: _searchController.text.isNotEmpty
                      ? IconButton(
                          icon: const Icon(Icons.clear_rounded, size: 20),
                          onPressed: () => _searchController.clear(),
                        )
                      : null,
                  filled: true,
                  fillColor: isDark ? AppColors.darkSurface : const Color(0xFFF4F1ED),
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(14),
                    borderSide: BorderSide.none,
                  ),
                  contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                ),
              ),
            ),
          const SizedBox(height: 4),
          Expanded(
            child: _isLoading && _rooms.isEmpty
                ? const Center(child: CircularProgressIndicator())
                : _filteredRooms.isEmpty
                    ? _buildEmptyState()
                    : RefreshIndicator(
                        onRefresh: () => _loadRooms(showLoading: true),
                        color: AppColors.primary,
                        child: ListView.builder(
                          padding: const EdgeInsets.only(left: 12, right: 12, top: 4, bottom: 100),
                          itemCount: _filteredRooms.length,
                          itemBuilder: (context, index) {
                            return _buildRoomTile(_filteredRooms[index], index);
                          },
                        ),
                      ),
          ),
        ],
      ),
      floatingActionButton: _isEditMode
          ? null
          : FloatingActionButton.extended(
              onPressed: () {
                Navigator.push(
                  context,
                  MaterialPageRoute(builder: (context) => const TxtImportScreen()),
                ).then((_) => _loadRooms(showLoading: true));
              },
              icon: const Icon(Icons.add_rounded),
              label: const Text('대화 추가'),
              backgroundColor: AppColors.primary,
              foregroundColor: Colors.white,
            ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.chat_bubble_outline_rounded, size: 64, color: context.textTertiary.withValues(alpha: 0.5)),
          const SizedBox(height: 16),
          Text('채팅방이 없습니다', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: context.textSecondary)),
        ],
      ),
    );
  }

  Widget _buildRoomTile(Map<String, dynamic> room, int index) {
    final roomId = room['roomId']?.toString() ?? '알 수 없는 방';
    final lastMessage = room['lastMessage']?.toString() ?? '';
    final lastTimestamp = room['lastTimestamp'] as int? ?? 0;
    final messageCount = room['messageCount'] as int? ?? 0;
    final lastSender = room['lastSender']?.toString() ?? '';
    final isSelected = _selectedRoomIds.contains(roomId);

    final displayName = _formatRoomName(roomId);
    final avatarColors = [AppColors.primary, AppColors.secondary, AppColors.featureFitting, const Color(0xFF6B7B8D), const Color(0xFFB67D5C)];
    final avatarColor = avatarColors[roomId.hashCode.abs() % avatarColors.length];

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: SoftCard(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        margin: const EdgeInsets.symmetric(vertical: 2),
        borderRadius: 16,
        onTap: _isEditMode
            ? () {
                setState(() {
                  if (isSelected) {
                    _selectedRoomIds.remove(roomId);
                  } else {
                    _selectedRoomIds.add(roomId);
                  }
                });
              }
            : () {
                Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (context) => ChatDetailScreen(roomId: roomId, displayName: displayName),
                  ),
                ).then((_) => _loadRooms());
              },
        child: Row(
          children: [
            if (_isEditMode) ...[
              Checkbox(
                value: isSelected,
                activeColor: AppColors.primary,
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
                onChanged: (val) {
                  setState(() {
                    if (val == true) {
                      _selectedRoomIds.add(roomId);
                    } else {
                      _selectedRoomIds.remove(roomId);
                    }
                  });
                },
              ),
              const SizedBox(width: 8),
            ],
            Container(
              width: 50,
              height: 50,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [avatarColor, avatarColor.withValues(alpha: 0.7)],
                ),
                borderRadius: BorderRadius.circular(16),
              ),
              child: Center(
                child: Text(
                  displayName.isNotEmpty ? displayName[0].toUpperCase() : '?',
                  style: const TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.w700),
                ),
              ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Expanded(
                        child: Text(
                          displayName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: context.textPrimary),
                        ),
                      ),
                      Text(_formatTimestamp(lastTimestamp), style: TextStyle(fontSize: 12, color: context.textTertiary)),
                    ],
                  ),
                  const SizedBox(height: 4),
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          lastSender.isNotEmpty ? '$lastSender: $lastMessage' : lastMessage,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: TextStyle(fontSize: 13, color: context.textSecondary),
                        ),
                      ),
                      if (messageCount > 0)
                        Container(
                          margin: const EdgeInsets.only(left: 8),
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(color: AppColors.primary.withValues(alpha: 0.1), borderRadius: BorderRadius.circular(10)),
                          child: Text('$messageCount', style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, color: AppColors.primary)),
                        ),
                    ],
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  String _formatRoomName(String roomId) => roomId;

  String _formatTimestamp(int timestamp) {
    if (timestamp == 0) return '';
    final dt = DateTime.fromMillisecondsSinceEpoch(timestamp);
    final now = DateTime.now();
    final diff = now.difference(dt);
    if (diff.inMinutes < 1) return '방금';
    if (diff.inHours < 1) return '${diff.inMinutes}분 전';
    if (diff.inDays < 1) return '${dt.hour.toString().padLeft(2, '0')}:${dt.minute.toString().padLeft(2, '0')}';
    if (diff.inDays < 7) return '${diff.inDays}일 전';
    return '${dt.month}/${dt.day}';
  }
}
