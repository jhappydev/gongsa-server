/* https://mediasoup.org/documentation/v3/mediasoup/installation/ */
/* 참고 코드: https://github.com/jamalag/mediasoup3/tree/main */

import express from 'express'
const app = express()

import https from 'httpolyglot'
import fs from 'fs'
import path from 'path'
const __dirname = path.resolve()

import { Server } from 'socket.io'
import mediasoup from 'mediasoup'
import jwt from 'jsonwebtoken'

import mysql from 'mysql2/promise'
import dotenv from 'dotenv';

dotenv.config();

const con = mysql.createPool({
  connectionLimit: 10,
  host: process.env.DB_HOST,
  port: '3306',
  user: process.env.DB_USER,
  password: process.env.DB_PASSWD,
  database: 'gongsa_ver2',
  dataStrings: true
})

app.get('*', (req, res, next) => {
  const path = '/sfu/'

  if (req.path.indexOf(path) == 0 && req.path.length > path.length) return next()

  res.send(`You need to specify a room name in the path e.g. 'https://127.0.0.1/sfu/room'`)
})

app.use('/sfu/:room', express.static(path.join(__dirname, 'public')))

// SSL cert for HTTPS access
const options = {
  ca: fs.readFileSync('/etc/letsencrypt/live/gong4media.shop/fullchain.pem', 'utf-8'),
  key: fs.readFileSync('/etc/letsencrypt/live/gong4media.shop/privkey.pem', 'utf-8'),
  cert: fs.readFileSync('/etc/letsencrypt/live/gong4media.shop/cert.pem', 'utf-8')
}

const httpsServer = https.createServer(options, app)
httpsServer.listen(3000, () => {
  console.log('listening on port: ' + 3000)
})

const io = new Server(httpsServer)

const connections = io.of('/mediasoup')

/**
 * Worker
 * |-> Router(s)
 *     |-> Producer Transport(s)
 *         |-> Producer
 *     |-> Consumer Transport(s)
 *         |-> Consumer 
 **/
let worker
let rooms = {} // { roomName1: { Router, rooms: [ sicketId1, ... ] }, ...}
let peers = {} // { socketId1: { roomName1, socket, transports = [id1, id2,] }, producers = [id1, id2,] }, consumers = [id1, id2,], peerDetails }, ...}
let transports = [] // [ { socketId1, roomName1, transport, consumer }, ... ]
let producers = [] // [ { socketId1, roomName1, producer, }, ... ]
let consumers = [] // [ { socketId1, roomName1, consumer, }, ... ]

const createWorker = async () => {
  worker = await mediasoup.createWorker({
    rtcMinPort: 40000,
    rtcMaxPort: 49999,
  })
  console.log(`worker pid ${worker.pid}`)

  worker.on('died', error => {
    console.error('mediasoup worker has died')
    setTimeout(() => process.exit(1), 2000) // exit in 2 seconds
  })

  return worker
}

worker = createWorker()

const mediaCodecs = [{
    kind: 'audio',
    mimeType: 'audio/opus',
    clockRate: 48000,
    channels: 2,
  },
  {
    kind: 'video',
    mimeType: 'video/VP8',
    clockRate: 90000,
    parameters: {
      'x-google-start-bitrate': 1000,
    },
  },
]

connections.on('connection', async socket => {
  console.log(socket.id)
  socket.emit('connection-success', {
    socketId: socket.id,
  })

  const removeItems = (items, socketId, type) => {
    items.forEach(item => {
      if (item.socketId === socket.id) {
        item[type].close()
      }
    })
    items = items.filter(item => item.socketId !== socket.id)

    return items
  }

  socket.on('disconnect', () => {
    // do some cleanup
    console.log('peer disconnected')
    consumers = removeItems(consumers, socket.id, 'consumer')
    producers = removeItems(producers, socket.id, 'producer')
    transports = removeItems(transports, socket.id, 'transport')

    const {
      roomName
    } = peers[socket.id]
    delete peers[socket.id]
    socket.leave(roomName)

    // remove socket from room
    rooms[roomName] = {
      router: rooms[roomName].router,
      peers: rooms[roomName].peers.filter(socketId => socketId !== socket.id)
    }
  })

  socket.use(async (event, next) => {
    const authorization = socket.handshake.headers.authorization ? socket.handshake.headers.authorization : ''
    const [tokenType, token] = authorization.split(' ')

    if (tokenType !== 'Bearer') {
      socket.emit('auth-error', {
        location: "auth",
        msg: '로그인 후 이용해주세요',
        data: ""
      })
      return
    }

    const secretKey = process.env.ACCESS_TOKEN_SECRET
    try {
      const decoded = jwt.verify(token, secretKey)
      console.log(decoded)
      if (decoded) {
        socket.userUID = decoded.userUID
      } else {
        socket.emit('auth-error', {
          location: "auth",
          msg: "로그인 후 이용해주세요.",
          data: ""
        })
      }
    } catch (err) {
      socket.emit('auth-error', {
        location: "auth",
        msg: "로그인 후 이용해주세요.",
        data: ""
      })
    } finally {
      next()
    }
  })

  socket.on('change', async ({
    studyTime,
    status
  }) => {
    const {
      roomName,
      userUID,
      studyMemberUID
    } = peers[socket.io]
    console.log(roomName, studyMemberUID)
    let sql = "UPDATE StudyMember SET studyStatus = ?, studyTime = ? WHERE UID = ?"
    const sqlData = [status, studyTime, studyMemberUID]
    await con.query(sql, sqlData)

    connections.to(roomName).emit('change', {
      userUID,
      studyTime,
      status
    })
  })

  socket.on('joinRoom', async ({
    groupUID,
    userUID,
    roomName
  }, callback) => {
    // create Router if it does not exist
    // const router1 = rooms[roomName] && rooms[roomName].get('data').router || await createRoom(roomName, socket.id)
    // 해당 roomName에 대한 Router가 있다면 Router를 반환하고, 아니라면 새로 만들어서 Router 반환
    const router1 = await createRoom(roomName, socket.id)
    socket.join(roomName)

    let getSql = "select UID from GroupMember where userUID = ? and groupUID = ?"
    const getSqlData = [userUID, groupUID]
    const [getResult] = await con.query(getSql, getSqlData)
    const groupMemberUID = getResult[0].UID

    let insertSql = "insert StudyMember(groupUID, groupMemberUID, userUID) values(?)"
    const insertSqlData = [groupUID, groupMemberUID, userUID]
    const [insertResult] = await con.query(insertSql, [insertSqlData])
    const studyMemberUID = insertResult.insertId

    console.log("studyMemberUID: " + studyMemberUID)

    peers[socket.id] = {
      socket,
      roomName, // Name for the Router this Peer joined
      studyMemberUID,
      userUID,
      transports: [],
      producers: [],
      consumers: [],
      peerDetails: {
        name: '',
        isAdmin: false, // Is this Peer the Admin?
      }
    }

    // get Router RTP Capabilities
    const rtpCapabilities = router1.rtpCapabilities

    // call callback from the client and send back the rtpCapabilities
    callback({
      rtpCapabilities
    })
    //callback({ rtpCapabilities, studyMemberUID })
  })

  socket.on('getStudyMemberUID', async (callback) => {
    const {
      studyMemberUID
    } = peers[socket.io]
    callback({
      studyMemberUID
    })
  })

  const createRoom = async (roomName, socketId) => {
    let router1
    let peers = []
    if (rooms[roomName]) {
      router1 = rooms[roomName].router
      peers = rooms[roomName].peers || []
    } else {
      router1 = await worker.createRouter({
        mediaCodecs,
      })
    }

    console.log(`Router ID: ${router1.id}`, peers.length)

    rooms[roomName] = {
      router: router1,
      peers: [...peers, socketId],
    }

    return router1
  }

  // consumer: false -> Producer, consumer: true -> Consumer
  socket.on('createWebRtcTransport', async ({
    consumer
  }, callback) => {
    const roomName = peers[socket.id].roomName

    // 해당 방의 Router를 조회
    const router = rooms[roomName].router

    // transport 생성
    createWebRtcTransport(router).then(
      transport => {
        callback({
          params: {
            id: transport.id,
            iceParameters: transport.iceParameters,
            iceCandidates: transport.iceCandidates,
            dtlsParameters: transport.dtlsParameters,
          }
        })

        addTransport(transport, roomName, consumer)
      },
      error => {
        console.log(error)
      })
  })

  // transport 저장 + 해당 peer의 정보에 transport 추가
  const addTransport = (transport, roomName, consumer) => {
    transports = [
      ...transports,
      {
        socketId: socket.id,
        transport,
        roomName,
        consumer,
      }
    ]

    peers[socket.id] = {
      ...peers[socket.id],
      transports: [
        ...peers[socket.id].transports,
        transport.id,
      ]
    }
  }

  // producer 저장 + 해당 peer의 정보에 producer 추가
  const addProducer = (producer, roomName) => {
    producers = [
      ...producers,
      {
        socketId: socket.id,
        producer,
        roomName,
      }
    ]

    peers[socket.id] = {
      ...peers[socket.id],
      producers: [
        ...peers[socket.id].producers,
        producer.id,
      ]
    }
  }

  // consumer 저장 + 해당 peer의 정보에 producer 추가
  const addConsumer = (consumer, roomName) => {
    consumers = [
      ...consumers,
      {
        socketId: socket.id,
        consumer,
        roomName,
      }
    ]

    peers[socket.id] = {
      ...peers[socket.id],
      consumers: [
        ...peers[socket.id].consumers,
        consumer.id,
      ]
    }
  }

  
  // 해당 room에 있는 producer 데이터 전송
  socket.on('getProducers', callback => {
    const { roomName } = peers[socket.id]

    let producerList = []

    producers.forEach(producerData => {
      if (producerData.socketId !== socket.id && producerData.roomName === roomName) {
        producerList = [...producerList, producerData.producer.id]
      }
    })

    callback(producerList)
  })

  const informConsumers = (roomName, socketId, id) => {
    console.log(`just joined, id ${id} ${roomName}, ${socketId}`)

    // 새로운 Producer 정보를 전달
    producers.forEach(producerData => {
      if (producerData.socketId !== socketId && producerData.roomName === roomName) { // 같은 방에서 나를 제외한 사람들
        const producerSocket = peers[producerData.socketId].socket
        // use socket to send producer id to producer
        producerSocket.emit('new-producer', {
          producerId: id
        })
      }
    })
  }

  const getTransport = (socketId) => {
    const [producerTransport] = transports.filter(transport => transport.socketId === socketId && !transport.consumer)
    return producerTransport.transport
  }

  // see client's socket.emit('transport-connect', ...)
  socket.on('transport-connect', ({
    dtlsParameters
  }) => {
    console.log('DTLS PARAMS... ', {
      dtlsParameters
    })

    getTransport(socket.id).connect({
      dtlsParameters
    })
  })

  // see client's socket.emit('transport-produce', ...)
  socket.on('transport-produce', async ({
    kind,
    rtpParameters,
    appData
  }, callback) => {
    // 해당 사용자의 producer 생성
    const producer = await getTransport(socket.id).produce({
      kind,
      rtpParameters,
    })

    const { roomName } = peers[socket.id]

    addProducer(producer, roomName)
    informConsumers(roomName, socket.id, producer.id)

    console.log('Producer ID: ', producer.id, producer.kind)

    producer.on('transportclose', () => {
      console.log('transport for this producer closed ')
      producer.close()
    })

    // Send back to the client the Producer's id
    callback({
      id: producer.id,
      producersExist: producers.length > 1 ? true : false
    })
  })

  // see client's socket.emit('transport-recv-connect', ...)
  socket.on('transport-recv-connect', async ({
    dtlsParameters,
    serverConsumerTransportId
  }) => {
    console.log(`DTLS PARAMS: ${dtlsParameters}`)
    const consumerTransport = transports.find(transportData => (
      transportData.consumer && transportData.transport.id == serverConsumerTransportId
    )).transport
    await consumerTransport.connect({
      dtlsParameters
    })
  })

  socket.on('consume', async ({
    rtpCapabilities,
    remoteProducerId, // 보내는 LP Id
    serverConsumerTransportId
  }, callback) => {
    try {

      const {
        roomName
      } = peers[socket.id]
      const router = rooms[roomName].router

      // consumerTransport 를 찾는다
      let consumerTransport = transports.find(transportData => (
        transportData.consumer && transportData.transport.id == serverConsumerTransportId
      )).transport

      // check if the router can consume the specified producer
      // LC가 consume 가능한지 확인
      if (router.canConsume({
          producerId: remoteProducerId,
          rtpCapabilities
        })) { // router가 remoteProducerId(LP)에 대해서 consume 가능할 때
        // transport can now consume and return a consumer
        // consumerTransport가 remoteProducerId(LC)에 대한 consumer 객체를 생성하고 미디어 스트림을 소비한다
        // remoteProducerId(LP)가 생성한 미디어 스트림을 수신하기 위한 RC 를 생성한다
        const consumer = await consumerTransport.consume({
          producerId: remoteProducerId,
          rtpCapabilities,
          paused: true,
        })

        consumer.on('transportclose', () => { // 데이터 전송이 닫힌 경우를 처리
          console.log('transport close from consumer')
        })

        consumer.on('producerclose', () => { // Producer가 종료된 경우를 처리
          console.log('producer of consumer closed')
          socket.emit('producer-closed', {
            remoteProducerId
          })

          consumerTransport.close([]) // 해당 producer에 대한 consumerTransport 종료
          transports = transports.filter(transportData => transportData.transport.id !== consumerTransport.id) // 해당 consumerTransport 삭제
          consumer.close() // consumer 종료
          consumers = consumers.filter(consumerData => consumerData.consumer.id !== consumer.id) // 해당 consumer 삭제
        })

        addConsumer(consumer, roomName)

        // from the consumer extract the following params
        // to send back to the Client
        const params = {
          id: consumer.id,
          producerId: remoteProducerId,
          kind: consumer.kind,
          rtpParameters: consumer.rtpParameters,
          serverConsumerId: consumer.id,
        }

        // send the parameters to the client
        callback({
          params
        })
      }
    } catch (error) {
      console.log(error.message)
      callback({
        params: {
          error: error
        }
      })
    }
  })

  socket.on('consumer-resume', async ({
    serverConsumerId
  }) => {
    console.log('consumer resume')
    const {
      consumer
    } = consumers.find(consumerData => consumerData.consumer.id === serverConsumerId)
    await consumer.resume()
  })
})

const createWebRtcTransport = async (router) => {
  return new Promise(async (resolve, reject) => {
    try {
      // https://mediasoup.org/documentation/v3/mediasoup/api/#WebRtcTransportOptions
      const webRtcTransport_options = {
        listenIps: [{
          ip: '172.31.15.252', // replace with relevant IP address
          announcedIp: '13.125.107.252',
        }],
        enableUdp: true,
        enableTcp: true,
        preferUdp: true,
      }

      // https://mediasoup.org/documentation/v3/mediasoup/api/#router-createWebRtcTransport
      let transport = await router.createWebRtcTransport(webRtcTransport_options)
      console.log(`transport id: ${transport.id}`)

      transport.on('dtlsstatechange', dtlsState => {
        if (dtlsState === 'closed') {
          transport.close()
        }
      })

      transport.on('close', () => {
        console.log('transport closed')
      })

      resolve(transport)

    } catch (error) {
      reject(error)
    }
  })
}