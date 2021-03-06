package com.criptext.mail.push

import android.app.NotificationManager
import android.content.Context
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import com.criptext.mail.androidtest.TestActivity
import com.criptext.mail.androidtest.TestDatabase
import com.criptext.mail.androidui.CriptextNotification
import com.criptext.mail.api.HttpClient
import com.criptext.mail.db.DeliveryTypes
import com.criptext.mail.db.EmailDetailLocalDB
import com.criptext.mail.db.models.*
import com.criptext.mail.mocks.MockEmailData
import com.criptext.mail.push.data.PushAPIRequestHandler
import com.criptext.mail.utils.DateUtils
import com.criptext.mail.utils.MockedResponse
import com.criptext.mail.utils.enqueueResponses
import io.mockk.mockk
import okhttp3.mockwebserver.MockWebServer
import org.amshove.kluent.shouldEqualTo
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.sql.Timestamp

@RunWith(AndroidJUnit4::class)
class PushRequestHandlerTest {

    @get:Rule
    val mActivityRule = ActivityTestRule(TestActivity::class.java)

    private val mockedThreadId = Timestamp(System.currentTimeMillis()).toString()
    private lateinit var db: TestDatabase
    private lateinit var mockWebServer: MockWebServer
    private val activeAccount = ActiveAccount(name = "Tester", recipientId = "tester",
            deviceId = 1, jwt = "__JWTOKEN__", signature = "")

    private lateinit var httpClient: HttpClient
    private lateinit var loadedEmails: List<FullEmail>
    private lateinit var emailDetailLocalDB: EmailDetailLocalDB

    @Before
    fun setup() {
        db = TestDatabase.getInstance(mActivityRule.activity)
        db.resetDao().deleteAllData(1)
        db.labelDao().insertAll(Label.DefaultItems().toList())
        emailDetailLocalDB = EmailDetailLocalDB.Default(db)
        mockWebServer = MockWebServer()
        mockWebServer.start()

        httpClient = HttpClient.Default(authScheme = HttpClient.AuthScheme.jwt,
                baseUrl = mockWebServer.url("/mock").toString(), connectionTimeout = 7000L,
                readTimeout = 7000L)
        loadedEmails = createEmailItemsInThread(4)
                .mapIndexed { _, fullEmail ->
                    // only the latter half are unread
                    fullEmail.email.unread = true
                    fullEmail
                }
        MockEmailData.insertEmailsNeededForTests(db, listOf(Label.defaultItems.inbox))
    }

    @After
    fun teardown() {
        mockWebServer.close()
    }

    private fun newHandler(): PushAPIRequestHandler =
            PushAPIRequestHandler(not = CriptextNotification(mActivityRule.activity), activeAccount = activeAccount,
                    manager = mActivityRule.activity
                            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager,
                    httpClient = httpClient)

    @Test
    fun should_move_to_trash_the_email_shown_on_the_push_notification() {


        mockWebServer.enqueueResponses(listOf(
                MockedResponse.Ok("Ok")
        ))

        val requestHandler = newHandler()
        requestHandler.trashEmail(loadedEmails.last().email.metadataKey, 0,
                emailDetailLocalDB, db.emailDao())

        val trashEmails = db.emailDao().getMetadataKeysFromLabel(Label.defaultItems.trash.id)


        trashEmails.size shouldEqualTo 1

    }


    @Test
    fun should_mark_as_read_the_email_shown_on_the_push_notification() {


        mockWebServer.enqueueResponses(listOf(
                MockedResponse.Ok("Ok")
        ))

        val requestHandler = newHandler()
        requestHandler.openEmail(loadedEmails.last().email.metadataKey, 0, db.emailDao())

        val readEmails = db.emailDao().getAll().filter { !it.unread }


        readEmails.size shouldEqualTo 1

    }

    private fun createEmailItemsInThread(size: Int): List<FullEmail> {
        return (1..size).map {
            FullEmail(
                    email = Email(id = it.toLong(),
                            content = """
                             <!DOCTYPE html>
                                <html>
                                <body>
                                    <h1>My $it Heading</h1>
                                    <p>My $it paragraph.</p>
                                </body>
                            </html>
                        """.trimIndent(),
                            date = DateUtils.getDateFromString(
                                    "1992-05-23 20:12:58",
                                    null),
                            delivered = DeliveryTypes.READ,
                            messageId = "key $it",
                            preview = "bodyPreview $it" ,
                            secure = true,
                            subject = "Subject $it",
                            threadId = mockedThreadId,
                            metadataKey = it + 100L,
                            unread = false,
                            isMuted = false,
                            unsentDate = DateUtils.getDateFromString(
                                    "1992-05-23 20:12:58",
                                    null),
                            trashDate = DateUtils.getDateFromString(
                                    "1992-05-23 20:12:58",
                                    null)),
                    labels = emptyList(),
                    to = emptyList(),
                    files = arrayListOf(CRFile(id = 0, token = "efhgfdgdfsg$it",
                            name = "test.pdf",
                            size = 65346L,
                            status = 1,
                            date = DateUtils.getDateFromString(
                                    "1992-05-23 20:12:58",
                                    null),
                            readOnly = false,
                            emailId = it.toLong()
                    )),
                    cc = emptyList(),
                    bcc = emptyList(),
                    from = Contact(1,"mayer@jigl.com", "Mayer Mizrachi"),
                    fileKey = null)
        }.reversed()
    }
}