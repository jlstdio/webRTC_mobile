//
//  SignalClient.swift
//  WebRTC
//
//  Created by Stasel on 20/05/2018.
//  Copyright © 2018 Stasel. All rights reserved.
//

import Foundation
import WebRTC
import Firebase

protocol SignalClientDelegate: AnyObject {
  func signalClientDidConnect(_ signalClient: SignalingClient)
  func signalClientDidDisconnect(_ signalClient: SignalingClient)
  func signalClient(_ signalClient: SignalingClient, didReceiveRemoteSdp sdp: RTCSessionDescription)
  func signalClient(_ signalClient: SignalingClient, didReceiveCandidate candidate: RTCIceCandidate)
}

final class SignalingClient {
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()
    weak var delegate: SignalClientDelegate?
    let ref = Database.database().reference()
    
    func deleteSdpAndCandidate(for person: String, for opposite: String) {
        /*
      Firestore.firestore().collection(person).document("sdp").delete() { err in
        if let err = err {
          print("Error removing firestore sdp: \(err)")
        } else {
          print("Firestore sdp successfully removed!")
        }
      }
      
      Firestore.firestore().collection(person).document("candidate").delete() { err in
        if let err = err {
          print("Error removing firestore candidate: \(err)")
        } else {
          print("Firestore candidate successfully removed!")
        }
      }*/
        ref.child("webRTC").child(person).removeValue()
        ref.child("webRTC").child(opposite).removeValue()
    }

  func send(sdp rtcSdp: RTCSessionDescription, to person: String) {
    do {
      let dataMessage = try self.encoder.encode(SessionDescription(from: rtcSdp))
      let dict = try JSONSerialization.jsonObject(with: dataMessage, options: .allowFragments) as! [String: Any]
        
        ref.child("webRTC").child(person).child("sdp").setValue(dict)
        /*
      Firestore.firestore().collection(person).document("sdp").setData(dict) { (err) in
        if let err = err {
          print("Error send sdp: \(err)")
        } else {
          print("Sdp sent!")
        }
      }*/
    }
    catch {
      debugPrint("Warning: Could not encode sdp: \(error)")
    }
  }
  
  func send(candidate rtcIceCandidate: RTCIceCandidate, to person: String) {
    do {
        let dataMessage = try self.encoder.encode(IceCandidate(from: rtcIceCandidate as! Decoder))
      let dict = try JSONSerialization.jsonObject(with: dataMessage, options: .allowFragments) as! [String: Any]
        
        var key = ref.childByAutoId().key as? String ?? ""
        ref.child("webRTC").child(person).child("candidates").child(key).setValue(dict)
        /*
      Firestore.firestore()
        .collection(person)
        .document("candidate")
        .collection("candidates")
        .addDocument(data: dict) { (err) in
          if let err = err {
            print("Error send candidate: \(err)")
          } else {
            print("Candidate sent!")
          }
      }*/
    }
    catch {
      debugPrint("Warning: Could not encode candidate: \(error)")
    }
  }
  
  func listenSdp(from person: String) {
      
      ref.child("webRTC").child(person).child("sdp").observe(.childAdded) { snapshot in
          
          let data = snapshot.value as! [String: Any]
          let sdp = data["sdp"] as! String
          let type = data["type"] as! String
          
          let jsonDict : [String: Any] = [
              "sdp": sdp,
              "type": type
          ] as Dictionary
          
          do {
            let jsonData = try JSONSerialization.data(withJSONObject: jsonDict, options: .prettyPrinted)
            let sessionDescription = try self.decoder.decode(SessionDescription.self, from: jsonData)
            self.delegate?.signalClient(self, didReceiveRemoteSdp: sessionDescription.rtcSessionDescription)
          }
          catch {
            debugPrint("Warning: Could not decode sdp data: \(error)")
            return
          }
      }
      
      /*
    Firestore.firestore().collection(person).document("sdp")
      .addSnapshotListener { documentSnapshot, error in
        guard let document = documentSnapshot else {
          print("Error fetching sdp: \(error!)")
          return
        }
        guard let data = document.data() else {
          print("Firestore sdp data was empty.")
          return
        }
        print("Firestore sdp data: \(data)")
        do {
          let jsonData = try JSONSerialization.data(withJSONObject: data, options: .prettyPrinted)
          let sessionDescription = try self.decoder.decode(SessionDescription.self, from: jsonData)
          self.delegate?.signalClient(self, didReceiveRemoteSdp: sessionDescription.rtcSessionDescription)
        }
        catch {
          debugPrint("Warning: Could not decode sdp data: \(error)")
          return
        }
    }*/
  }
  
  func listenCandidate(from person: String) {
      
      ref.child("webRTC").child(person).child("candidates").observe(.childAdded) { snapshot in
          
          let data = snapshot.value as! [String: Any]
          let sdp = data["sdp"] as! String
          let sdpMLineIndex = data["sdpMLineIndex"] as! Int
          let sdpMid = data["sdpMid"] as! Int
          
          let jsonDict : [String: Any] = [
              "sdp": sdp,
              "sdpMLineIndex": sdpMLineIndex,
              "sdpMid" : sdpMid
          ] as Dictionary
          
          do {
            let jsonData = try JSONSerialization.data(withJSONObject: jsonDict, options: .prettyPrinted)
            let iceCandidate = try self.decoder.decode(IceCandidate.self, from: jsonData)
            self.delegate?.signalClient(self, didReceiveCandidate: iceCandidate.rtcIceCandidate)
          }
          catch {
            debugPrint("Warning: Could not decode candidate data: \(error)")
          }
      }
      
      /*
    Firestore.firestore()
      .collection(person)
      .document("candidate")
      .collection("candidates")
      .addSnapshotListener { (querySnapshot, err) in
        guard let documents = querySnapshot?.documents else {
          print("Error fetching documents: \(err!)")
          return
        }

        querySnapshot!.documentChanges.forEach { diff in
          if (diff.type == .added) {
            do {
              let jsonData = try JSONSerialization.data(withJSONObject: documents.first!.data(), options: .prettyPrinted)
              let iceCandidate = try self.decoder.decode(IceCandidate.self, from: jsonData)
              self.delegate?.signalClient(self, didReceiveCandidate: iceCandidate.rtcIceCandidate)
            }
            catch {
              debugPrint("Warning: Could not decode candidate data: \(error)")
              return
            }
          }
        }
    }
      
      */
  }
}
