package com.bakaroo.bingo.backend.feature.wallet.entity;

import com.bakaroo.bingo.backend.feature.user.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name="wallet_withdrawal_requests", indexes={@Index(name="idx_withdrawals_status_created",columnList="status,created_at"),@Index(name="idx_withdrawals_player_created",columnList="player_id,created_at")})
public class WithdrawalRequest {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="player_id",nullable=false) private User player;
 @Column(name="operation_id",nullable=false,length=36,unique=true) private String operationId;
 @Column(nullable=false,precision=19,scale=2) private BigDecimal amount;
 @Column(nullable=false,length=3) private String currency;
 @Column(name="destination_type",nullable=false,length=12) private String destinationType;
 @Column(name="account_name",nullable=false,length=120) private String accountName;
 @Column(name="phone_number",length=30) private String phoneNumber;
 @Column(name="bank_name",length=100) private String bankName;
 @Column(name="account_number",length=80) private String accountNumber;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private WithdrawalStatus status;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="reviewed_by") private User reviewedBy;
 @Column(name="reviewed_at") private Instant reviewedAt;
 @Column(name="transfer_reference",length=120) private String transferReference;
 @Column(name="review_note",length=500) private String reviewNote;
 protected WithdrawalRequest() {}
 public static WithdrawalRequest create(User p,String op,BigDecimal amount,String currency,String type,String name,String phone,String bank,String account,Instant now){var r=new WithdrawalRequest();r.player=p;r.operationId=op;r.amount=amount;r.currency=currency;r.destinationType=type;r.accountName=name;r.phoneNumber=phone;r.bankName=bank;r.accountNumber=account;r.status=WithdrawalStatus.PENDING;r.createdAt=now;return r;}
 public void complete(User reviewer,String reference,String note,Instant now){status=WithdrawalStatus.COMPLETED;reviewedBy=reviewer;reviewedAt=now;transferReference=reference;reviewNote=note;}
 public void reject(User reviewer,String note,Instant now){status=WithdrawalStatus.REJECTED;reviewedBy=reviewer;reviewedAt=now;reviewNote=note;}
 public Long getId(){return id;} public User getPlayer(){return player;} public String getOperationId(){return operationId;} public BigDecimal getAmount(){return amount;} public String getCurrency(){return currency;} public String getDestinationType(){return destinationType;} public String getAccountName(){return accountName;} public String getPhoneNumber(){return phoneNumber;} public String getBankName(){return bankName;} public String getAccountNumber(){return accountNumber;} public WithdrawalStatus getStatus(){return status;} public Instant getCreatedAt(){return createdAt;} public User getReviewedBy(){return reviewedBy;} public Instant getReviewedAt(){return reviewedAt;} public String getTransferReference(){return transferReference;} public String getReviewNote(){return reviewNote;}
}
