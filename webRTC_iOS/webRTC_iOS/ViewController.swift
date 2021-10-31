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
        
        
        // retrieve image & convert to bytes -> NSData
        let bytes = getArrayOfBytesFromImage(imageData: self.imagePreview.image!.pngData()! as NSData)
        let data: NSData = NSData(bytes: bytes, length: bytes.count)
        
        // get chunks
        let chunks = sliceToChunk(data: data)
        
        self.webRTCClient.offer { (sdp) in
          self.signalClient.send(sdp: sdp, to: self.oppositePerson)
        }
    }
    
    @IBAction func ReceiveFile(_ sender: Any) {
        self.webRTCClient.answer { (localSdp) in
          self.signalClient.send(sdp: localSdp, to: self.oppositePerson)
        }
    }
    
    func getArrayOfBytesFromImage(imageData:NSData) -> Array<UInt8>
    {

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
    
    func sliceToChunk(data: NSData) -> [Data]{
        
        let dataLen = (data as NSData).length
        let fullChunks = Int(dataLen / 1024) // 1 Kbyte
        let totalChunks = fullChunks + (dataLen % 1024 != 0 ? 1 : 0)
        var diff = 1024 // (preset) max size of each chunk
        var chunks: [Data] = [Data]() // chunks: we will use this
        
        // split data as 'diff' save it to 'chunks'
        for chunkCounter in 0..<totalChunks
        {
            var chunk:Data
            
            let chunkBase = chunkCounter * 1024
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

extension ViewController: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    
    func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
        
        var newImage: UIImage? = nil // update 할 이미지
        
        if let possibleImage = info[UIImagePickerController.InfoKey.editedImage] as? UIImage {
            newImage = possibleImage // 수정된 이미지가 있을 경우
        } else if let possibleImage = info[UIImagePickerController.InfoKey.originalImage] as? UIImage {
            newImage = possibleImage // 원본 이미지가 있을 경우
        }
        
        self.imagePreview.image = newImage // 받아온 이미지를 update
        picker.dismiss(animated: true, completion: nil) // picker를 닫아줌
        
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
    DispatchQueue.main.async {
      let message = String(data: data, encoding: .utf8) ?? "(Binary: \(data.count) bytes)"
      let alert = UIAlertController(title: "Message from WebRTC", message: message, preferredStyle: .alert)
      alert.addAction(UIAlertAction(title: "OK", style: .cancel, handler: nil))
      self.present(alert, animated: true, completion: nil)
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
