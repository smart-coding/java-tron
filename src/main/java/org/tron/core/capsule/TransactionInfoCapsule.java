package org.tron.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.tron.core.exception.BadItemException;
import org.tron.protos.Protocol.TransactionInfo;

@Slf4j
public class TransactionInfoCapsule implements ProtoCapsule<TransactionInfo> {

  private TransactionInfo transactionInfo;

  /**
   * constructor TransactionCapsule.
   */
  public TransactionInfoCapsule(TransactionInfo trxRet) {
    this.transactionInfo = trxRet;
  }

  public TransactionInfoCapsule(byte[] data) throws BadItemException {
    try {
      this.transactionInfo = TransactionInfo.parseFrom(data);
    } catch (InvalidProtocolBufferException e) {
      throw new BadItemException("TransactionInfoCapsule proto data parse exception");
    }
  }

  public TransactionInfoCapsule() {
    this.transactionInfo = TransactionInfo.newBuilder().build();
  }

  public long getFee() {
    return transactionInfo.getFee();
  }

  public void setId(byte[] id) {
    this.transactionInfo = this.transactionInfo.toBuilder()
        .setId(ByteString.copyFrom(id)).build();
  }

  public byte[] getId() {
    return transactionInfo.getId().toByteArray();
  }

  public void setFee(long fee) {
    this.transactionInfo = this.transactionInfo.toBuilder().setFee(fee).build();
  }

  public void addFee(long fee) {
    this.transactionInfo = this.transactionInfo.toBuilder()
        .setFee(this.transactionInfo.getFee() + fee).build();
  }

  public long getBlockNumber() {
    return transactionInfo.getBlockNumber();
  }

  public void setBlockNumber(long num) {
    this.transactionInfo = this.transactionInfo.toBuilder().setBlockNumber(num)
        .build();
  }

  public long getBlockTimeStamp() {
    return transactionInfo.getBlockTimeStamp();
  }

  public void setBlockTimeStamp(long time) {
    this.transactionInfo = this.transactionInfo.toBuilder().setBlockTimeStamp(time)
        .build();
  }

  @Override
  public byte[] getData() {
    return this.transactionInfo.toByteArray();
  }

  @Override
  public TransactionInfo getInstance() {
    return this.transactionInfo;
  }
}