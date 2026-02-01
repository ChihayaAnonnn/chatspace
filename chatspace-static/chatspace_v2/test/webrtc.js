var websocket;

const userid = 1;

function hasUserMedia() {
    navigator.getUserMedia = navigator.getUserMedia || navigator.msGetUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia;
    return !!navigator.getUserMedia;
}

function hasRTCPeerConnection() {
    window.RTCPeerConnection = window.RTCPeerConnection || window.webkitRTCPeerConnection || window.mozRTCPeerConnection || window.msRTCPeerConnection;
    return !!window.RTCPeerConnection;
}

var yourVideo = document.getElementById("yours");
var theirVideo = document.getElementById("theirs");
var Connection;

createSocket();
startPeerConnection();

function createSocket(){
    websocket = new WebSocket('wss://chatspace.iceclean.top:19019/space/ws/chat', ["eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiJmZDFjNDMzNDk4NGE0NjAxYWI4YjhmZDkyZTU5ZjY2OSIsInN1YiI6IjEiLCJpc3MiOiJJY2VDbGVhbiIsImlhdCI6MTY3MTYyNjAwOCwiZXhwIjoxNjcxNjI5NjA4fQ.FEh1R4FhvKmpN5RlyPZH_RPxcYvNf_68PwLmKtquWBo", 0]); // 使用 WebSocket 连接到服务器
    // websocket = new WebSocket('ws://chatspace.iceclean.top:19019/space/ws/chat', ["eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiJmYWM1YTdiZTRhNjg0YjkzOTY0NzU0NzE4MDZkNDkwNSIsInN1YiI6IjciLCJpc3MiOiJJY2VDbGVhbiIsImlhdCI6MTY3MTU1OTU2NywiZXhwIjoxNjcxNTYzMTY3fQ.uqeky3BLcU71WqFUxYWOCGH-oJPor-zlKx56CuBzJv0", 0]); // 使用 WebSocket 连接到服务器
    eventBind();
}

function startPeerConnection() {
    var config = {
        iceServers: [
            { urls: 'stun:stun.l.google.com:19302' },
            { urls: 'stun:global.stun.twilio.com:3478?transport=udp' }
        ],
    };
    Connection = new RTCPeerConnection(config);
    Connection.onicecandidate = function(e) {
        console.log('onicecandidate');
        if (e.candidate) {
            websocket.send(JSON.stringify({
                "wsType": "RTC_CANDIDATE",
                "wsContent": {
                    "candidate": e.candidate
                }
            }));
        }
    }
    Connection.onaddstream = function(e) {
        console.log('onaddstream');
        //theirVideo.src = window.URL.createObjectURL(e.stream);
        theirVideo.srcObject = e.stream;
    }
}

function createOffer(){
    //发送offer和answer的函数，发送本地session描述
    Connection.createOffer().then(offer => {
        Connection.setLocalDescription(offer);
        websocket.send(JSON.stringify({
            "wsType": "RTC_OFFER",
            "wsContent": {
                "sdp": offer
            }
        }));
    });
}

function eventBind() {
    //连接成功
    websocket.onopen = function(e) {
        console.log('连接成功')
    };
    //server端请求关闭
    websocket.onclose = function(e) {
        console.log('close')
    };
    //error
    websocket.onerror = function(e) {

    };
//收到消息
    console.log("绑定")
    websocket.onmessage = (event)=> {
        let data = JSON.parse(event.data);
        let type = data.wsType;
        let content = data.wsContent;
        console.log(content);
        switch(type) {
            case "RTC_CANDIDATE":
                console.log("candidate!")
                // 如果是一个 ICE 的候选，则将其加入到 PeerConnection 中，否则设定对方的session描述为传递过来的描述
                if (content.candidate) {
                    Connection.addIceCandidate(new RTCIceCandidate(content.candidate));
                }
                break;
            case "RTC_OFFER":
                console.log("offer!");
                Connection.setRemoteDescription(JSON.parse(content).sdp);
                Connection.createAnswer().then(answer => {
                    Connection.setLocalDescription(answer);
                    console.log(window.stream)
                    websocket.send(JSON.stringify({
                        "wsType": "RTC_ANSWER",
                        "wsContent": {
                            "sdp": answer
                        }
                    }));
                });
                break;
            case "RTC_ANSWER":
                console.log("answer!")
                Connection.setRemoteDescription(JSON.parse(content).sdp);
                console.log(window.stream)
                break;
        }
    };
}
