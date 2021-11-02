//
//  ViewController.swift
//  webRTC_iOS
//
//  Created by Joonhee Lee on 2021/10/30.
//

import UIKit
import AVFoundation
import Foundation
import WebRTC

class ViewController: UIViewController {
    
    private let imagePicker = UIImagePickerController()
    
    private let signalClient = SignalingClient()
    private let webRTCClient = WebRTCClient(iceServers: Config.default.webRTCIceServers)
    
    private var currentPerson = ""
    private var oppositePerson = ""
    private var sender = false
    var chunks = [Data]()
    var receivedChunks = Data()
    var dataLen = 0
    var totalChunks = 0
    var chunksCount = 0
    
    @IBOutlet weak var myName: UITextField!
    @IBOutlet weak var destinationName: UITextField!
    @IBOutlet weak var imagePreview: UIImageView!
    @IBOutlet weak var statusField: UILabel!
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        self.webRTCClient.delegate = self
        self.signalClient.delegate = self
        
        self.imagePicker.sourceType = .photoLibrary // 앨범에서 가져옴
        self.imagePicker.allowsEditing = true // 수정 가능 여부
        self.imagePicker.delegate = self // picker delegate
    }
    
    @IBAction func ImagePick(_ sender: Any) {
        self.present(self.imagePicker, animated: true)
        
        // set my name & destination name
        self.currentPerson = myName.text ?? "myName"
        self.oppositePerson = destinationName.text ?? "oppositeName"
        
        // listen for incoming SDP & Candidate
        self.signalClient.listenSdp(from: self.currentPerson)
        self.signalClient.listenCandidate(from: self.currentPerson)
    }
    
    @IBAction func SendFile(_ sender: Any) {
        
        self.sender = true
        
        self.webRTCClient.offer { (sdp) in
          self.signalClient.send(sdp: sdp, to: self.oppositePerson)
        }
    }
    
    @IBAction func ReceiveFile(_ sender: Any) {
        self.webRTCClient.answer { (localSdp) in
          self.signalClient.send(sdp: localSdp, to: self.oppositePerson)
        }
    }
    
    @IBAction func SendData(_ sender: Any) {
        
        let head = ("send/" + String(dataLen) + "/" + String(totalChunks)).data(using: .utf8)
        
        self.webRTCClient.sendData(head!)
    }
    
    @IBAction func Abort(_ sender: Any) {
        self.signalClient.deleteSdpAndCandidate(for: self.currentPerson)
        self.webRTCClient.closePeerConnection()
        self.webRTCClient.createPeerConnection()
    }
}

extension ViewController: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    
    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
        
        var newImage: UIImage? = nil // update 할 이미지
        
        if let possibleImage = info[UIImagePickerController.InfoKey.editedImage] as? UIImage {
            newImage = possibleImage // 수정된 이미지가 있을 경우
        } else if let possibleImage = info[UIImagePickerController.InfoKey.originalImage] as? UIImage {
            newImage = possibleImage // 원본 이미지가 있을 경우
        }
        
        self.imagePreview.image = newImage // 받아온 이미지를 update
        
        // retrieve image & convert to bytes -> NSData
        let bytes = getArrayOfBytesFromImage(imageData: newImage!.pngData()! as NSData)
        let data: NSData = NSData(bytes: bytes, length: bytes.count)
        chunks = sliceToChunk(data: data)
        
        picker.dismiss(animated: true, completion: nil) // picker를 닫아줌
        
    }
    
    func getArrayOfBytesFromImage(imageData:NSData) -> Array<UInt8> {

      // the number of elements:
      let count = imageData.length / MemoryLayout<Int8>.size

      // create array of appropriate length:
      var bytes = [UInt8](repeating: 0, count: count)

      // copy bytes into array
      imageData.getBytes(&bytes, length:count * MemoryLayout<Int8>.size)

      var byteArray:Array = Array<UInt8>()

      for i in 0 ..< count {
        byteArray.append(bytes[i])
      }

      return byteArray
    }
    
    func sliceToChunk(data: NSData) -> [Data] {
        
        var diff = 1024 * 10 // (preset) max size of each chunk
        dataLen = (data as NSData).length
        let fullChunks = Int(dataLen / diff) // 1 Kbyte
        totalChunks = fullChunks + (dataLen % diff != 0 ? 1 : 0)
        
        // split data as 'diff' save it to 'chunks'
        for chunkCounter in 0..<totalChunks
        {
            var chunk:Data
            
            let chunkBase = chunkCounter * diff
            if chunkCounter == totalChunks - 1
            {
                diff = dataLen - chunkBase
            }
                
            let range: NSRange = NSRange(chunkBase ..< (chunkBase + diff))
            chunk = data.subdata(with: range)
                
            chunks.append(chunk)
        }
            
        // whole data length
        print("data length is")
        print(dataLen)
        
        print("Number of Full chunks is")
        print(fullChunks)
        
        // splited data
        debugPrint(chunks)
        
        return chunks
    }
}


extension ViewController: SignalClientDelegate {
  func signalClientDidConnect(_ signalClient: SignalingClient) {
    //self.signalingConnected = true
  }
  
  func signalClientDidDisconnect(_ signalClient: SignalingClient) {
    //self.signalingConnected = false
  }
  
  func signalClient(_ signalClient: SignalingClient, didReceiveRemoteSdp sdp: RTCSessionDescription) {
    print("Received remote sdp")
    self.webRTCClient.set(remoteSdp: sdp) { (error) in
      //self.hasRemoteSdp = true
    }
  }
  
  func signalClient(_ signalClient: SignalingClient, didReceiveCandidate candidate: RTCIceCandidate) {
    print("Received remote candidate")
      //self.remoteCandidateCount += 1
      self.webRTCClient.set(remoteCandidate: candidate)
  }
}

extension ViewController: WebRTCClientDelegate {
  
  func webRTCClient(_ client: WebRTCClient, didDiscoverLocalCandidate candidate: RTCIceCandidate) {
    print("discovered local candidate")
    //self.localCandidateCount += 1
    self.signalClient.send(candidate: candidate, to: self.oppositePerson)
  }
  
  func webRTCClient(_ client: WebRTCClient, didChangeConnectionState state: RTCIceConnectionState) {
      
      let textColor: UIColor
      switch state {
      case .connected, .completed:
          textColor = .green
      case .disconnected:
        textColor = .orange
      case .failed, .closed:
          textColor = .red
      case .new, .checking, .count:
          textColor = .black
      @unknown default:
          textColor = .black
      }
      DispatchQueue.main.async {
          self.statusField?.text = state.description.capitalized
          self.statusField?.textColor = textColor
      }
  }
  
    func webRTCClient(_ client: WebRTCClient, didReceiveData data: Data) {
        DispatchQueue.main.async { [self] in
            let message = String(data: data, encoding: .utf8) ?? "body"
            
            if message.contains("body") {
             
                self.receivedChunks.append(data)
                
                if (self.chunksCount >= self.totalChunks) {
                    self.dataLen = 0
                    self.totalChunks = 0
                    self.chunksCount = 0
                    
                    let head = "done".data(using: .utf8)
                    self.webRTCClient.sendData(head!)
                    
                    print("total chunks are \(receivedChunks.count)")
                    
                    self.imagePreview.image = UIImage(data: receivedChunks)
                    
                    let alert = UIAlertController(title: "Message from WebRTC", message: "RECEIVED", preferredStyle: .alert)
                    alert.addAction(UIAlertAction(title: "OK", style: .cancel, handler: nil))
                    self.present(alert, animated: true, completion: nil)
                }
                else {
                    print("requiring \(self.chunksCount)/\(self.totalChunks)")
                    let head = ("require/" + String(self.chunksCount)).data(using: .utf8)
                    self.webRTCClient.sendData(head!)
                    self.chunksCount += 1
                }
            }
            
            else if message.contains("done") {
                
                let alert = UIAlertController(title: "Message from WebRTC", message: "SENT!", preferredStyle: .alert)
                alert.addAction(UIAlertAction(title: "OK", style: .cancel, handler: nil))
                self.present(alert, animated: true, completion: nil)
            }
            
            else if message.starts(with: "require") {
                let buff = message.split(separator: "/")
                self.webRTCClient.sendData(self.chunks[Int(buff[1])!])
                
                print("sending \(self.chunksCount)/\(self.totalChunks)")
            }
            
            else if message.starts(with: "send") {
                let buff = message.split(separator: "/")
                self.dataLen = Int(buff[1])!
                self.totalChunks = Int(buff[2])!
                self.chunksCount = 0
                self.receivedChunks = Data()
                
                print("dataLen is \(self.dataLen)")
                print("totalChunks are \(self.totalChunks)")
                
                print("requiring \(self.chunksCount)/\(self.totalChunks)")
                
                let head = ("require/" + String(self.chunksCount)).data(using: .utf8)
                self.webRTCClient.sendData(head!)
                self.chunksCount += 1
                
            }
        
            //let alert = UIAlertController(title: "Message from WebRTC", message: message, preferredStyle: .alert)
            //alert.addAction(UIAlertAction(title: "OK", style: .cancel, handler: nil))
            //self.present(alert, animated: true, completion: nil)
        }
    }
}

extension ViewController: UITextFieldDelegate {
  
    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        if textField == myName {
            destinationName.becomeFirstResponder()
        }
        else {
            destinationName.resignFirstResponder()
        }
        return true
    }
}

extension String {

    func toImage(_ handler: @escaping ((UIImage?)->())) {
        if let url = URL(string: self) {
            URLSession.shared.dataTask(with: url) { (data, response, error) in
                if let data = data {
                    let image = UIImage(data: data)
                    handler(image)
                }
            }.resume()
        }
    }
}
