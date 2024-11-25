// vision.controller.ts
import { Controller, Get, Put, Param, HttpStatus, HttpException, Logger } from '@nestjs/common'
import { VisionService } from './vision.service'
import { ApiOperation, ApiResponse } from '@nestjs/swagger'

@Controller()
export class VisionController {
  private readonly logger = new Logger(VisionController.name)

  constructor(private readonly service: VisionService) {}

  @Get('/list/:token')
  @ApiOperation({
    summary: 'List ids (UUID) of medias',
    description: 'List of mediaIds (UUID) of type {type} linked to identity represented by token {token}',
  })
  @ApiResponse({ status: HttpStatus.OK, description: 'OK' })
  @ApiResponse({ status: HttpStatus.BAD_REQUEST, description: 'Check arguments or expired token' })
  @ApiResponse({ status: HttpStatus.FORBIDDEN, description: 'Permission Denied.' })
  @ApiResponse({ status: HttpStatus.INTERNAL_SERVER_ERROR, description: 'Server error, please retry.' })
  async listMedias(@Param('token') token: string) {
    if (!token) {
      throw new HttpException('Invalid token', HttpStatus.BAD_REQUEST)
    }

    try {
      const medias = await this.service.listMedias(token)
      return medias
    } catch (e) {
      this.logger.error(e.message, e.stack)
      throw new HttpException('Internal Server Error', HttpStatus.INTERNAL_SERVER_ERROR)
    }
  }

  @Put('/success/:token')
  @ApiOperation({
    summary: 'Notify a successful verification or capture',
    description: 'Notify a successful verification or capture for identity represented by token {token}.',
  })
  @ApiResponse({ status: HttpStatus.OK, description: 'OK' })
  async success(@Param('token') token: string) {
    if (!token) {
      throw new HttpException('Invalid token', HttpStatus.BAD_REQUEST)
    }

    try {
      this.logger.log(`Vision: success: tokenId: ${token}`)
      await this.service.success(token)
      return { statusCode: HttpStatus.OK }
    } catch (e) {
      this.logger.error(e.message, e.stack)
      throw new HttpException('Internal Server Error', HttpStatus.INTERNAL_SERVER_ERROR)
    }
  }

  @Put('/failure/:token')
  @ApiOperation({
    summary: 'Notify a failed verification or capture',
    description: 'Notify a failed verification or capture for identity represented by token {token}.',
  })
  @ApiResponse({ status: HttpStatus.OK, description: 'OK' })
  async failure(@Param('token') token: string) {
    if (!token) {
      throw new HttpException('Invalid token', HttpStatus.BAD_REQUEST)
    }

    try {
      this.logger.log(`Vision: failure: tokenId: ${token}`)
      await this.service.failure(token)
      return { statusCode: HttpStatus.OK }
    } catch (e) {
      this.logger.error(e.message, e.stack)
      throw new HttpException('Internal Server Error', HttpStatus.INTERNAL_SERVER_ERROR)
    }
  }
}
