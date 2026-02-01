// client.js 文件
const socket = new WebSocket('ws://chatspace.iceclean.top:19019/space/ws/chat', ["eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiI3MzYwMzZiZjAwZDU0MzcyYjIyYTBlNzUwZjNiMWQzZiIsInN1YiI6IjciLCJpc3MiOiJJY2VDbGVhbiIsImlhdCI6MTY3MTU1MTU1OCwiZXhwIjoxNjcxNTU1MTU4fQ.A_6JFR3U9Mt6r7ECZsOSR_yU2mppwfQvRzIR6laVjxI", 0]); // 使用 WebSocket 连接到服务器
let localStream; // 本地音频流
let remoteStream; // 远程音频流
let peerConnection; // WebRTC 连接
let username; // 用户名

// 获取 DOM 元素
const connectButton = document.getElementById('connect');
const usernameInput = document.getElementById('username');
const voiceChatContainer = document.getElementById('voice-chat-container');
const connectedUsersContainer = document.getElementById('connected-users');
const micVolume = document.getElementById('mic-volume');
const micStatus = document.getElementById('mic-status');
const speakerStatus = document.getElementById('speaker-status');
const toggleMicButton = document.getElementById('toggle-mic');
const toggleSpeakerButton = document.getElementById('toggle-speaker');
const hangupButton = document.getElementById('hangup');

// 用于保存连接的用户
let connectedUsers = {};

// 用于保存本地麦克风和扬声器的状态
let micEnabled = false;
let speakerEnabled = false;

// 处理连接按钮的点击事件
connectButton.addEventListener('click', () => {
    // 获取用户名
    username = usernameInput.value;
    // 向服务器发送连接请求
    socket.send(JSON.stringify({ type: 'connect', name: username }));
    // 隐藏输入用户名的输入框，显示语音聊天界面
    usernameInput.style.display = 'none';
    connectButton.style.display = 'none';
    voiceChatContainer.style.display = 'block';
});

// 处理麦克风开关按钮的点击事件
toggleMicButton.addEventListener('click', () => {
    // 如果麦克风当前是打开的，则关闭它
    if (micEnabled) {
        localStream.getAudioTracks()[0].enabled = false;
        micStatus.innerText = 'Mic: Off';
        micEnabled = false;
    }
    // 否则，打开麦克风
    else {
        localStream.getAudioTracks()[0].enabled = true;
        micStatus.innerText = 'Mic: On';
        micEnabled = true;
    }
});

// 处理扬声器开关按钮的点击事件
toggleSpeakerButton.addEventListener('click', () => {
    // 如果扬声器当前是打开的，则关闭它
    if (speakerEnabled) {
        remoteStream.getAudioTracks()[0].enabled = false;
        speakerStatus.innerText = 'Speaker: Off';
        speakerEnabled = false;
    }
    // 否则，打开扬声器
    else {
        remoteStream.getAudioTracks()[0].enabled = true;
        speakerStatus.innerText = 'Speaker: On';
        speakerEnabled = true;
    }
});

// 处理挂断按钮的点击事件
hangupButton.addEventListener('click', () => {
    // 关闭 WebRTC 连接
    peerConnection.close();
    // 关闭本地和远程音频流
    localStream.getTracks().forEach(track => track.stop());
    remoteStream.getTracks().forEach(track => track.stop());
    // 重置本地和远程音频流、WebRTC 连接
    localStream = null;
    remoteStream = null;
    peerConnection = null;
    // 重置用户列表
    connectedUsers = {};
    connectedUsersContainer.innerHTML = '';
    // 隐藏语音聊天界面，显示输入用户名的输入框
    voiceChatContainer.style.display = 'none';
    usernameInput.style.display = 'block';
    connectButton.style.display = 'block';
});

// 处理服务器发送的消息
socket.addEventListener('message', event => {
    const data = JSON.parse(event.data);
    switch (data.type) {
        // 处理服务器发送的 Offer 消息
        case 'offer':
            // 创建 WebRTC 连接
            createPeerConnection();
            // 设置远程 SDP
            peerConnection.setRemoteDescription(new RTCSessionDescription(data.sdp));
            // 发送 Answer
            sendAnswer(data.from);
            break;
        // 处理服务器发送的 Answer 消息
        case 'answer':
            // 设置远程 SDP
            peerConnection.setRemoteDescription(new RTCSessionDescription(data.sdp));
            break;
        // 处理服务器发送的 IceCandidate 消息
        case 'ice-candidate':
            // 将 IceCandidate 添加到 WebRTC 连接中
            peerConnection.addIceCandidate(new RTCIceCandidate(data.candidate));
            break;
        // 处理服务器发送的用户列表更新消息
        case 'update-users':
            // 更新用户列表
            connectedUsers = data.users;
            updateConnectedUsers();
            break;
    }
});

// 创建 WebRTC 连接
function createPeerConnection() {
    // 创建 RTCPeerConnection 实例
    peerConnection = new RTCPeerConnection();
    // 将本地音频流添加到 WebRTC 连接中
    peerConnection.addStream(localStream);
    // 监听远程媒体流的添加事件
    peerConnection.addEventListener('addstream', event => {
        // 将远程媒体流绑定到音量控件上
        micVolume.srcObject = event.stream;
        // 保存远程媒体流
        remoteStream = event.stream;
    });
    // 监听 IceCandidate 事件
    peerConnection.addEventListener('icecandidate', event => {
        // 如果 IceCandidate 有效，则向服务器发送 IceCandidate 消息
        if (event.candidate) {
            socket.send(JSON.stringify({
                type: 'ice-candidate',
                candidate: event.candidate
            }));
        }
    });
}

// 发送 Offer
function sendOffer(to) {
    // 创建 Offer
    peerConnection.createOffer()
        .then(sdp => {
            // 设置本地 SDP
            peerConnection.setLocalDescription(sdp);
            // 向服务器发送 Offer 消息
            socket.send(JSON.stringify({
                type: 'offer',
                sdp: sdp,
                to: to
            }));
        })
        .catch(error => console.error(error));
}

// 发送 Answer
function sendAnswer(to) {
    // 创建 Answer
    peerConnection.createAnswer()
        .then(sdp => {
            // 设置本地 SDP
            peerConnection.setLocalDescription(sdp);
            // 向服务器发送 Answer 消息
            socket.send(JSON.stringify({
                type: 'answer',
                sdp: sdp,
                to: to
            }));
        })
        .catch(error => console.error(error));
}

// 更新用户列表
function updateConnectedUsers() {
    // 清空用户列表
    connectedUsersContainer.innerHTML = '';
    // 遍历所有用户
    connectedUsers.forEach(user => {
        // 创建用户列表项
        const listItem = document.createElement('li');
        listItem.innerText = user;
        listItem.className = 'user';
        // 将用户列表项添加到用户列表中
        connectedUsersContainer.appendChild(listItem);
    });
}

// 麦克风开关
function toggleMic() {
    // 判断麦克风是否已开启
    if (localStream.getAudioTracks()[0].enabled) {
        // 关闭麦克风
        localStream.getAudioTracks()[0].enabled = false;
        // 更新按钮状态
        toggleMicButton.innerText = '开启麦克风';
    } else {
        // 开启麦克风
        localStream.getAudioTracks()[0].enabled = true;
        // 更新按钮状态
        toggleMicButton.innerText = '关闭麦克风';
    }
}

// 扬声器开关
function toggleSpeaker() {
    // 判断扬声器是否已开启
    if (remoteStream.getAudioTracks()[0].enabled) {
        // 关闭扬声器
        remoteStream.getAudioTracks()[0].enabled = false;
        // 更新按钮状态
        toggleSpeakerButton.innerText = '开启扬声器';
    } else {
        // 开启扬声器
        remoteStream.getAudioTracks()[0].enabled = true;
        // 更新按钮状态
        toggleSpeakerButton.innerText = '关闭扬声器';
    }
}

// 调整音量
function adjustVolume(volumeControl, volume) {
    // 设置音量
    volumeControl.volume = volume;
}

// 发起连接
function connect(user) {
    // 判断 WebRTC 连接是否已存在
    if (connectedUsers[user]) {
        console.log('连接已存在');
        return;
    }
    // 创建 WebRTC 连接
    createPeerConnection();
    // 保存 WebRTC 连接
    connectedUsers[user] = peerConnection;
    // 发送 Offer
    sendOffer(user);
}

// 断开连接
function disconnect(user) {
    // 判断 WebRTC 连接是否已存在
    if (!connectedUsers[user]) {
        console.log('连接不存在');
        return;
    }
    // 关闭 WebRTC 连接
    connectedUsers[user].close();
    // 删除 WebRTC 连接
    delete connectedUsers[user];
}

// 设置事件监听器
function setEventListeners() {
    // 点击用户列表项时发起连接
    connectedUsersContainer.addEventListener('click', event => {
        if (event.target.tagName === 'LI') {
            // 获取用户名
            const user = event.target.innerText;
            // 发起连接
            connect(user);
        }
    });
    // 点击麦克风按钮时开关麦克风
    toggleMicButton.addEventListener('click', toggleMic);
    // 点击扬声器按钮时开关扬声器
    toggleSpeakerButton.addEventListener('click', toggleSpeaker);
    // 调整麦克风音量时调整音量
    micVolume.addEventListener('input', event => {
        adjustVolume(localStream, event.target.value);
    });
    // 调整扬声器音量时调整音量
    // speakerVolume.addEventListener('input', event => {
    //     adjustVolume(remoteStream, event.target.value);
    // });
}
