package io.github.mbannour.mongo.codecs.models

import org.bson.types.ObjectId

// Animal hierarchy
sealed trait Animal
case class Dog(name: String, breed: String, age: Int) extends Animal
case class Cat(name: String, age: Int, indoor: Boolean) extends Animal
case class Bird(name: String, species: String, canFly: Boolean) extends Animal

// Order system with sealed trait
sealed trait OrderStatus
case class Pending(orderId: String, timestamp: Long) extends OrderStatus
case class Processing(orderId: String, workerId: String, progress: Int) extends OrderStatus
case class Completed(orderId: String, completedAt: Long, rating: Option[Int]) extends OrderStatus
case class Cancelled(orderId: String, reason: String) extends OrderStatus

case class Order(_id: ObjectId, customerId: String, status: OrderStatus, total: Double)

// Payment methods
sealed trait PaymentMethod
case class CreditCardPayment(cardNumber: String, expiryMonth: Int, expiryYear: Int) extends PaymentMethod
case class PayPalPayment(email: String, transactionId: String) extends PaymentMethod
case class BankTransferPayment(accountNumber: String, bankCode: String) extends PaymentMethod

case class Invoice(_id: ObjectId, invoiceNumber: String, paymentMethod: PaymentMethod, amount: Double)

// Notification system
sealed trait NotificationChannel
case class EmailChannel(to: String, subject: String, body: String) extends NotificationChannel
case class SmsChannel(phoneNumber: String, message: String) extends NotificationChannel
case class PushChannel(deviceToken: String, title: String, body: String) extends NotificationChannel

case class NotificationLog(_id: ObjectId, userId: String, channel: NotificationChannel, sentAt: Long, delivered: Boolean)

// Complex nested sealed traits
sealed trait UserAction
case class LoginAction(username: String, ipAddress: String, timestamp: Long) extends UserAction
case class PurchaseAction(userId: String, orderId: String, amount: Double) extends UserAction
case class ProfileUpdateAction(userId: String, fields: Map[String, String]) extends UserAction

case class AuditLog(_id: ObjectId, action: UserAction, metadata: Option[Map[String, String]])
