package com.my.nft.l0.daemon

import cats.effect.Async
import com.my.nft.shared_data.calculated_state.CalculatedStateService

object NotificationDaemon {
  case class EmailConfig(
    smtpHost: String,
    smtpPort: Int,
    username: String,
    password: String,
    fromAddress: String,
    enabled: Boolean = false
  )

  // No-op daemon for demo; real email sending removed to avoid javax.mail deps
  def startDaemon[F[_]: Async](
    config: EmailConfig,
    calculatedStateService: CalculatedStateService[F]
  ): F[Unit] = Async[F].unit

  def defaultConfig: EmailConfig = EmailConfig(
    smtpHost = "",
    smtpPort = 0,
    username = "",
    password = "",
    fromAddress = "noreply@insurecheck.com",
    enabled = false
  )
}
